package controllers

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

import javax.inject.Inject
import models._
import org.joda.time.DateTime
import play.api.Logger
import play.api.{ Configuration, Environment }
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

class UserController @Inject() (environment: play.api.Environment, configuration: play.api.Configuration, val reactiveMongoApi: ReactiveMongoApi, val messagesApi: MessagesApi) extends Controller with MongoController with ReactiveMongoComponents with I18nSupport {
  /*
   * Note that the `collection` is not a `val`, but a `def`. We do _not_ store
   * the collection reference to avoid potential problems in development with
   * Play hot-reloading.
   */
  def collection: Future[JSONCollection] = database.map(_.collection[JSONCollection]("users"))

  def untrail(path: String) = Action { 
    MovedPermanently("/" + path)
  }

  def Email = Action.async { implicit request: Request[AnyContent] =>
    val errorFunction = { formWithErrors: Form[FormDTO] =>
      implicit lazy val config = configuration
      Future.successful(BadRequest(views.html.query(formWithErrors, List.empty[Map[String, String]])))
    }

    val successFunction = { dto: FormDTO =>
      val query = Query(BSONObjectID.generate(), dto.bedrooms, dto.rentlo, dto.renthi, dto.places, DateTime.now(), DateTime.now().plusDays(-10))
      val modifier = Json.obj("$push" -> Json.obj("queries" -> Json.toJson(query)))
      collection.flatMap(col => col.update(Json.obj("email" -> dto.email), modifier, col.db.connection.options.writeConcern, true)).map {
        lastError =>
        Logger.debug(s"Successfully inserted with LastError: $lastError")
        Redirect(routes.UserController.getEmail(dto.email))
      }
    }
    FormDTO.form.bindFromRequest.fold(errorFunction, successFunction)
  }

  def getForCursor(futcursor: Future[Cursor[User]]) = Action.async { implicit request: Request[AnyContent] =>
    implicit lazy val config = configuration
    futcursor.flatMap(_.collect[List](-1, Cursor.FailOnError[List[User]]())).map { users =>
      users.headOption map { user => Ok(views.html.queries(FormDTO.form, user.email, user.id, user.queries)) } getOrElse Ok(views.html.queries(FormDTO.form, "", BSONObjectID.generate(), List.empty))
    }.recover {
      case e =>
        e.printStackTrace()
        BadRequest(e.getMessage())
    }
  }

  def getUid(uid: reactivemongo.bson.BSONObjectID) =
    getForCursor(collection.map(_.find(Json.obj("_id" -> Json.obj("$oid" -> uid.stringify))).cursor[User]()))

  def getEmail(email: String) = 
    getForCursor(collection.map(_.find(Json.obj("email" -> email)).cursor[User]()))

  def delete(
    uid: reactivemongo.bson.BSONObjectID,
    qid: reactivemongo.bson.BSONObjectID) = Action.async { request =>

    val modifier = Json.obj("$pull" -> Json.obj("queries" -> Json.obj("_id" -> Json.obj("$oid" -> qid.stringify))))
    collection.flatMap(col => col.update(Json.obj("_id" -> Json.obj("$oid" -> uid.stringify)), modifier, col.db.connection.options.writeConcern, true)).map {
      lastError =>
      Logger.debug(s"Successfully inserted with LastError: $lastError")
      Redirect(routes.UserController.getUid(uid))
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
        (result \ "places").validate[Set[String]].get, DateTime.now(), new DateTime())
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
