package controllers

import scala.concurrent.Future
import scala.language.postfixOps

import javax.inject.Inject
import models._
import org.joda.time.DateTime
import play.api.Logger
import play.api.data.Form
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.{Cursor, FailoverStrategy}
import reactivemongo.api.commands.{Command, CursorFetcher}
import reactivemongo.bson.BSONObjectID
// BSON-JSON conversions/collection
import reactivemongo.play.json._
import reactivemongo.play.json.collection._

class UserController @Inject() (val reactiveMongoApi: ReactiveMongoApi, val messagesApi: MessagesApi) extends Controller with MongoController with ReactiveMongoComponents with I18nSupport {
  /*
   * Note that the `collection` is not a `val`, but a `def`. We do _not_ store
   * the collection reference to avoid potential problems in development with
   * Play hot-reloading.
   */
  def collection: Future[JSONCollection] = database.map(_.collection[JSONCollection]("users"))

  def index = Action.async { implicit request: Request[AnyContent] =>
    request.session.get("email").map { email =>
      val futcursor = collection.map(_.find(Json.obj("email" -> email)).cursor[User]())

      // build (asynchronously) a list containing all the users
      futcursor.flatMap(_.collect[List](-1, Cursor.FailOnError[List[User]]())).map { users =>
        users.headOption map { user => Ok(views.html.queries(user.id, user.queries)) } getOrElse Ok(views.html.queries(BSONObjectID.generate(), List.empty))
      }.recover {
        case e =>
          e.printStackTrace()
          BadRequest(e.getMessage())
      }
    }.getOrElse {
      Future.successful(Unauthorized("Oops, I don't have your email."))
    }
  }

  def untrail(path: String) = Action { 
    MovedPermanently("/" + path)
  }

  def Email = Action { implicit request: Request[AnyContent] =>
    val errorFunction = { formWithErrors: Form[Query.DTO] =>
      BadRequest(views.html.query(formWithErrors, routes.QueryController.Update(), routes.UserController.Email(), List.empty[Map[String, String]]))
    }

    val successFunction = { data: Query.DTO =>
      Query.form = Query.form.fill(data)
      Redirect(routes.UserController.index())
    }

    Query.form.bindFromRequest.fold(errorFunction, successFunction)
  }

  def delete(
    uid: reactivemongo.bson.BSONObjectID,
    qid: reactivemongo.bson.BSONObjectID) = Action.async { request =>
    request.session.get("email").map { email =>
      val commandDoc = Json.obj(
        "update" -> "users",
        "query" -> Json.obj("email" -> email),
        "update" -> Json.obj("$pull" -> Json.obj("queries" -> Json.obj("id" -> qid))))
      val runner = Command.run(JSONSerializationPack, FailoverStrategy())
      database.map(db =>
        runner.apply(db, runner.rawCommand(commandDoc)).one[JsObject](db.connection.options.readPreference).recover {
          case e =>
            e.printStackTrace()
            BadRequest(e.getMessage())
        }
      )
      Future.successful(Redirect(routes.UserController.index()))
    }.getOrElse {
      Future.successful(Unauthorized("Oops, I don't have your email."))
    }
  }

  def get(email: String) = Action.async {
    val futcursor = collection.map(_.find(Json.obj("email" -> email)).cursor[User]())
    futcursor.flatMap(_.collect[List](-1, Cursor.FailOnError[List[User]]())).map { users =>
      Ok(Json.arr(
        users.headOption.getOrElse(new User(BSONObjectID.generate(), "", List.empty)).queries
      ))
    }
  }

  def createFromJson = Action.async(parse.json) { request =>
    import play.api.libs.json.Reads._
    /*
     * request.body is a JsValue.
     * There is an implicit Writes that turns this JsValue as a JsObject,
     * so you can call insert() with this JsValue.
     * (insert() takes a JsObject as parameter, or anything that can be
     * turned into a JsObject using a Writes.)
     */

    val transformer: Reads[JsObject] =
      Reads.jsPickBranch[JsArray](__ \ "bedrooms") and
        Reads.jsPickBranch[JsNumber](__ \ "rentlo") and
        Reads.jsPickBranch[JsNumber](__ \ "renthi") and
        Reads.jsPickBranch[JsArray](__ \ "places") reduce

    request.body.transform(transformer).map { result =>
      val query = Query(BSONObjectID.generate(), (result \ "bedrooms").validate[Set[Int]].get,
        (result \ "rentlo").validate[Int].get, (result \ "renthi").validate[Int].get,
        (result \ "places").validate[Set[String]].get, DateTime.now())
      val modifier = Json.obj("$push" -> Json.obj("queries" -> Json.toJson(query)))
      collection.flatMap(_.findAndUpdate(Json.obj("email" -> "alicia.shi@gmail.com"), modifier, true, true)
        .map { lastError =>
          Logger.debug(s"Successfully inserted with LastError: $lastError")
          Created
        }
      )
    }.getOrElse(Future.successful(BadRequest("invalid json")))
  }
}
