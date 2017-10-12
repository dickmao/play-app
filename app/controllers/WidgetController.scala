package controllers

import javax.inject.Inject

import models.Widget
import play.api.data._
import play.api.i18n._
import play.api.mvc._
import play.api.Logger
import com.redis._
import play.api.{ Configuration, Environment }
import com.google.maps._
import com.google.gson._

/**
 * The classic WidgetController using I18nSupport.
 *
 * I18nSupport provides implicits that create a Messages instances from
 * a request using implicit conversion.
  */
class WidgetController @Inject() (environment: play.api.Environment, configuration: play.api.Configuration, val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import WidgetForm._

  private val links = scala.collection.mutable.ArrayBuffer.empty[String]
  private val postUrl = routes.WidgetController.Update()
  private val redisClient = new RedisClient("localhost", 6379)
  private val context = new GeoApiContext.Builder().apiKey(configuration.getString("googleapi.key").get).build()
  def manhattan(acs: Array[model.AddressComponent]) : String = { Option(acs.find(x => x.types.contains(model.AddressComponentType.SUBLOCALITY)).getOrElse(acs.find(x => x.types.contains(model.AddressComponentType.LOCALITY)).getOrElse(new model.AddressComponent())).shortName).getOrElse("") }
  def q_shared_locality(p0_locality: String, p1_radmem: GeoRadiusMember) : Boolean = {
    val p1_placeid = Option(GeocodingApi.reverseGeocode(context, new model.LatLng(p1_radmem.coords.get._1.toDouble, p1_radmem.coords.get._2.toDouble)).await().headOption.getOrElse(new model.GeocodingResult()).placeId).getOrElse("")
    val p1_details = Option(PlacesApi.placeDetails(context, p1_placeid).awaitIgnoreError()).getOrElse(new model.PlaceDetails())
    return p0_locality == manhattan(p1_details.addressComponents)
  }

  def index = Action {
    Ok(views.html.index())
  }

  def Display = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.listWidgets(form, postUrl, checkbeds, configuration, links))
  }

  // This will be the action that handles our form post
  def Update = Action { implicit request: Request[AnyContent] =>
    val errorFunction = { formWithErrors: Form[Data] =>
      BadRequest(views.html.listWidgets(formWithErrors, postUrl, checkbeds, configuration, links))
    }

    val successFunction = { data: Data =>
      val ilo = data.rentlo.getOrElse("0").replaceAll("\\D+", "").toInt
      val ihi = data.renthi.getOrElse(Widget.TooDear).replaceAll("\\D+", "").toInt
      val (small, big) = (Set(0,1), Set(2,3,4,5))
      val bedrooms = if (data.bedrooms.contains(0)) small else Set[Int]() ++ (if (data.bedrooms.contains(2)) big else Set())
      val widget = Widget(bedrooms = bedrooms, rentlo = ilo.min(ihi), renthi = ihi.max(ilo), place = data.autocomplete)
      val byprice = redisClient.zrangebyscore("item.index.price", ilo.min(ihi).toDouble, true, ihi.max(ilo).toDouble, true, None)
      val bybeds = redisClient.zrangebyscore("item.index.bedrooms", bedrooms.min.toDouble, true, bedrooms.max.toDouble, true, None)
      val p0_geores = GeocodingApi.geocode(context, data.autocomplete).await().headOption.getOrElse(new model.GeocodingResult())
      val proximate = redisClient.georadius("item.geohash.coords", p0_geores.geometry.location.lng, p0_geores.geometry.location.lat, 1.5, "km", true, false, false, None, None, None, None).getOrElse(List())
      Logger.debug(p0_geores.addressComponents.map(x => x.shortName).mkString(" "))
      val proximate_and_colocal = proximate.filter(x => q_shared_locality(manhattan(p0_geores.addressComponents), x.get))
      val intersect = byprice.getOrElse(List()).toSet.intersect(bybeds.getOrElse(List()).toSet).intersect(proximate_and_colocal.map(x => x.get.member.get).toSet)
      links.clear()
      links ++= intersect.map(x => redisClient.hget(x, "link").get)
      Logger(this.getClass()).debug(links.toString())
      Redirect(routes.WidgetController.Display())
    }

    form.bindFromRequest.fold(errorFunction, successFunction)
  }
}

