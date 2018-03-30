package controllers

import com.redis._
import javax.inject.Inject
import models._
import play.api.{Configuration, Environment}
import play.api.data._
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc._
import play.api.routing._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import success_function.SuccessFunction
import org.joda.time.format.{ ISODateTimeFormat, DateTimeFormat }
import com.github.nscala_time.time.Imports.DateTimeOrdering

/**
 * The classic QueryController using I18nSupport.
 *
 * I18nSupport provides implicits that create a Messages instances from
 * a request using implicit conversion.
  */
class QueryController @Inject() (environment: play.api.Environment, configuration: play.api.Configuration, val messagesApi: MessagesApi) extends Controller with I18nSupport {
  private val rediscp = new RedisClientPool(configuration.getString("redis.host").getOrElse("redis"),
    configuration.getInt("redis.port").getOrElse(6379))
  def Test = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.test(FormDTO.form, configuration))
  }

  def EmptyQueryAction() = Action { implicit request: Request[AnyContent] =>
    implicit lazy val config = configuration
    Ok(views.html.query(FormDTO.form, List()))
  }

  def popmax(id1: String, id2: String) : String = {
    val f1 = rediscp.withClient {
      _.hmget("geoitem." + id1, "featureclass", "featurecode", "population").get
    }
    val f2 = rediscp.withClient {
      _.hmget("geoitem." + id2, "featureclass", "featurecode", "population").get
    }
    return if (f1("population").toInt > f2("population").toInt) id1 else id2
  }

  def fetch = Action { implicit request: Request[AnyContent] =>
    val prefix = request.getQueryString("query").get.toLowerCase()
    val names = if (prefix.isEmpty) configuration.getString("dropdown_prepopulate").getOrElse("").split(",").toList.map(name => s"$name:$name") else rediscp.withClient { client => {
        client.zrangebylex("geoitem.index.name", "[%s".format(prefix), "(%s{".format(prefix), Some((0,7))).getOrElse(List()).sortBy(x => client.hget("geoitem." + client.smembers("georitem." + x.split(":")(1)).get.flatten.reduceLeft(popmax), "population").get.toInt)(Ordering[Int].reverse)
      }
    }
    Ok(Json.toJson(Map("results" -> names.map(name => Map("name" -> name.split(":")(1),
      "value" -> name.split(":")(1)) ))))
  }

  def javascriptRoutes = Action { implicit request: Request[AnyContent] =>
    Ok(
        JavaScriptReverseRouter("jsRoutes")(
          routes.javascript.QueryController.fetch
        )
    ).as("text/javascript")
  }

  // This will be the action that handles our form post
  def Update = Action.async { implicit request: Request[AnyContent] =>
    val errorFunction = { formWithErrors: Form[FormDTO] =>
      implicit lazy val config = configuration
      Future.successful(BadRequest(views.html.query(formWithErrors, List())))
    }

    val successFunction = { query: FormDTO =>
      implicit lazy val env = environment
      implicit lazy val config = configuration
      SuccessFunction.successFunction(query).map(l =>
        Ok(views.html.query(FormDTO.form.fill(query), l.sortBy(x => ISODateTimeFormat.dateTimeParser().parseDateTime(x("posted")))(DateTimeOrdering.reverse))))
    }
    FormDTO.form.bindFromRequest.fold(errorFunction, successFunction)
  }
}
