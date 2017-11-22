package controllers

import javax.inject.Inject

import models.Widget
import play.api.data._
import play.api.i18n._
import play.api.mvc._
import play.api.libs.json._
import play.api.Logger
import play.api.{ Configuration, Environment }
import play.api.routing._
import scala.collection.immutable._
import com.github.nscala_time.time.Imports._
import org.joda.time.format.ISODateTimeFormat
import geocode._
import com.redis._

/**
 * The classic WidgetController using I18nSupport.
 *
 * I18nSupport provides implicits that create a Messages instances from
 * a request using implicit conversion.
  */
class WidgetController @Inject() (environment: play.api.Environment, configuration: play.api.Configuration, val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import WidgetForm._

  private val links = scala.collection.mutable.ArrayBuffer.empty[String]
  private val titles = scala.collection.mutable.ArrayBuffer.empty[String]
  private val postUrl = routes.WidgetController.Update()
  private val rediscp = new RedisClientPool(configuration.getString("redis.hostname").getOrElse("redis"),
    configuration.getInt("redis.port").getOrElse(6379))

  def index = Action {
    Ok(views.html.index())
  }

  def Display = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.listWidgets(form, postUrl, checkbeds, configuration, links, titles))
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
    val names = rediscp.withClient {
      client => {
        client.zrangebylex("geoitem.index.name", "[%s".format(prefix), "(%s{".format(prefix), Some((0,5))).getOrElse(List()).sortBy(x => client.hget("geoitem." + client.smembers("georitem." + x.split(":")(1)).get.flatten.reduceLeft(popmax), "population").get.toInt)(Ordering[Int].reverse)
      }
    }
    Ok(Json.toJson(Map("suggestions" -> names.map(_.split(":")(1)))))
  }

  def javascriptRoutes = Action { implicit request: Request[AnyContent] =>
    Ok(
        JavaScriptReverseRouter("jsRoutes")(
          routes.javascript.WidgetController.fetch
        )
    ).as("text/javascript")
  }

  // This will be the action that handles our form post
  def Update = Action { implicit request: Request[AnyContent] =>
    val errorFunction = { formWithErrors: Form[Data] =>
      BadRequest(views.html.listWidgets(formWithErrors, postUrl, checkbeds, configuration, links, titles))
    }

    val successFunction = { data: Data =>
      form = form.fill(data)
      val ilo = data.rentlo.getOrElse("$0").replaceAll("\\D+", "").toInt
      val ihi = data.renthi.getOrElse(Widget.TooDear).replaceAll("\\D+", "").toInt
      val (small, big) = (Set(0,1), Set(2,3,4,5))
      val bedrooms = Set[Int]() ++ (if (data.bedrooms.contains(0)) small else Set()) ++ (if (data.bedrooms.contains(2)) big else Set())
      val widget = Widget(bedrooms = bedrooms, rentlo = ilo.min(ihi), renthi = ihi.max(ilo), place = data.autocomplete)
      val byprice = rediscp.withClient {
        _.zrangebyscore("item.index.price", ilo.min(ihi).toDouble, true, ihi.max(ilo).toDouble, true, None).getOrElse(List())
      }
      val bybeds = rediscp.withClient {
        _.zrangebyscore("item.index.bedrooms", bedrooms.min.toDouble, true, bedrooms.max.toDouble, true, None).getOrElse(List())
      }

      val matches = rediscp.withClient {
        _.zrangebylex("geoitem.index.name", "[%s".format(data.autocomplete.toLowerCase), "(%s{".format(data.autocomplete.toLowerCase), None).getOrElse(List[String]())
      }
      val geonameids = rediscp.withClient {
        client => {
          matches.flatMap(mat => client.smembers("georitem." + mat.split(":")(1)).getOrElse(Set())).flatten
        }
      }
      titles.clear()
      links.clear()
      if (!geonameids.isEmpty) {
        val nyp = new ReverseGeoCode(environment.resourceAsStream("NY.P.tsv").get, true)
        val p0 = geonameids.reduceLeft(popmax)
        val p0_fields = rediscp.withClient {
          _.hmget("geoitem." + p0, "longitude", "latitude", "admin2code", "featurecode").get
        }
        val dist = if (p0_fields("featurecode").matches("PPLA.*")) 25 else 1.5
        val proximate = rediscp.withClient {
          _.georadius("item.geohash.coords", p0_fields("longitude"), p0_fields("latitude"), dist, "km", true, false, false, None, None, None, None).getOrElse(List()).flatten
        }
        val proximate_and_colocal = rediscp.withClient {
          client => {
            proximate.filter(p1 => p0_fields("admin2code") == client.hget("geoitem." + nyp.nearestPlace(p1.coords.get._2.toDouble, p1.coords.get._1.toDouble).id, "admin2code").getOrElse(""))
          }
        }
        val intersect = byprice.toSet.intersect(bybeds.toSet).intersect(proximate_and_colocal.map(x => x.member.get).toSet)
        val latestFirst = rediscp.withClient {
          client => {
            intersect.toList.sortBy(x => ISODateTimeFormat.dateTimeParser().parseDateTime(client.hget("item." + x, "posted").get))(DateTimeOrdering.reverse)
          }
        }

        links ++= rediscp.withClient {
          client => { latestFirst.map(x => client.hget("item." + x, "link").get) }
        }
        titles ++= rediscp.withClient {
          client => { latestFirst.map(x => client.hget("item." + x, "title").get) } }
      }
      Redirect(routes.WidgetController.Display())
    }

    form.bindFromRequest.fold(errorFunction, successFunction)
  }
}
