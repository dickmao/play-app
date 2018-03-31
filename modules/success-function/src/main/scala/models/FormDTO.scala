package models

case class FormDTO(
bedrooms: Set[Int], 
rentlo: Int, 
renthi: Int, 
places: Set[String]
)

object FormDTO {
  import play.api.data._
  import play.api.data.Forms._
  import play.api.libs.json._

  val TooDear = "$7000"

  case class CheckBeds(value: Int, name: String)
  val checkbeds = Seq(CheckBeds(0, "0-1BR"), CheckBeds(2, "2BR+"))

  val form = Form[FormDTO](
    mapping(
      "checkbeds" -> list(number(min=0)),
      "rentlo" -> optional(text),
      "renthi" -> optional(text),
      "autocomplete" -> nonEmptyText
    ) {
      (checkbeds, rentlo, renthi, autocomplete) =>
      val ilo = rentlo.getOrElse("$0").replaceAll("\\D+", "").toInt
      val ihi = renthi.getOrElse(TooDear).replaceAll("\\D+", "").toInt
      val places = autocomplete.split(",").toSet
      FormDTO(checkbeds.toSet, ilo, ihi, places)
    } { dto =>
      val formatter = java.text.NumberFormat.getIntegerInstance()
      Some(
        (
          dto.bedrooms.toList,
          Some(s"$$${formatter.format(dto.rentlo.toLong)}"),
          Some(s"$$${formatter.format(dto.renthi.toLong)}"),
          dto.places.mkString(",")
        ))
    }).fill(FormDTO(Set(0), 500, 4000, Set.empty))
}
