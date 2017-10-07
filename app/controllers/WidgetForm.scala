package controllers

object WidgetForm {
  import play.api.data.Forms._
  import play.api.data.Form

  /**
   * A form processing DTO that maps to the form below.
   *
   * Using a class specifically for form binding reduces the chances
   * of a parameter tampering attack and makes code clearer.
   */
  case class Data(bedrooms: Int, rentlo: Option[String], renthi: Option[String], autocomplete: String)

  /**
   * The form definition for the "create a widget" form.
   * It specifies the form fields and their types,
   * as well as how to convert from a Data to form data and vice versa.
   */
  val form = Form(
    mapping(
      "bedrooms" -> number(min = 0),
      "rentlo" -> optional(text),
      "renthi" -> optional(text),
      "autocomplete" -> nonEmptyText
    )(Data.apply)(Data.unapply)
  )

  case class CheckBeds(value: Int, name: String)
  val checkbeds = Seq(CheckBeds(0, "0-1BR"), CheckBeds(1, "2BR+"))
 
}

