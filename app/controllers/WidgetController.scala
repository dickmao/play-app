package controllers

import javax.inject.Inject

import models.Widget
import play.api.data._
import play.api.i18n._
import play.api.mvc._
import org.sedis._
import org.sedis.Dress._

/**
 * The classic WidgetController using I18nSupport.
 *
 * I18nSupport provides implicits that create a Messages instances from
 * a request using implicit conversion.
 */
class WidgetController @Inject()(val messagesApi: MessagesApi, val sedisPool: Pool) extends Controller with I18nSupport {
  import WidgetForm._

  // val bar: String = sedisPool.withJedisClient(client => client.get("foo"))
  private val postUrl = routes.WidgetController.Update()

  def index = Action {
    Ok(views.html.index())
  }

  def listWidgets = Action { implicit request: Request[AnyContent] =>
    // Pass an unpopulated form to the template
     Ok(views.html.listWidgets(form, postUrl, checkbeds))
  }

  // This will be the action that handles our form post
  def Update = Action { implicit request: Request[AnyContent] =>
    val errorFunction = { formWithErrors: Form[Data] =>
      // This is the bad case, where the form had validation errors.
      // Let's show the user the form again, with the errors highlighted.
      // Note how we pass the form with errors to the template.
      BadRequest(views.html.listWidgets(formWithErrors, postUrl, checkbeds))
    }

    val successFunction = { data: Data =>
      val ilo = data.rentlo.getOrElse("0").replaceAll("\\D+", "").toInt
      val ihi = data.renthi.getOrElse(Widget.TooDear).replaceAll("\\D+", "").toInt
      val widget = Widget(bedrooms = data.bedrooms, rentlo = ilo.min(ihi), renthi = ihi.max(ilo), place = data.autocomplete)
      Redirect(routes.WidgetController.listWidgets())
    }

    form.bindFromRequest.fold(errorFunction, successFunction)
  }
}

