package models
import org.joda.time.DateTime
import reactivemongo.bson.BSONObjectID

case class Query(
id: BSONObjectID,
bedrooms: Set[Int], 
rentlo: Int, 
renthi: Int, 
places: Set[String],
createdAt: DateTime,
lastEmailed: DateTime,
email: String
)

object Query {
  val TooDear = "$7000"

  import play.api.data._
  import play.api.data.Forms._
  import play.api.libs.json._
  import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
  import play.api.libs.functional.syntax._

  implicit val QueryFormat: OFormat[Query] = (
    (__ \ "_id").format[BSONObjectID] and
      (__ \ "bedrooms").format[Set[Int]] and
      (__ \ "rentlo").format[Int] and
      (__ \ "renthi").format[Int] and
      (__ \ "places").format[Set[String]] and
      (__ \ "createdAt").format[DateTime] and
      (__ \ "lastEmailed").format[DateTime] and
      (__ \ "email").format[String]
  )(Query.apply, unlift(Query.unapply))

  case class CheckBeds(value: Int, name: String)
  val checkbeds = Seq(CheckBeds(0, "0-1BR"), CheckBeds(2, "2BR+"))

  var form = Form[Query](
    mapping(
      "checkbeds" -> list(number(min=0)),
      "rentlo" -> optional(text),
      "renthi" -> optional(text),
      "autocomplete" -> nonEmptyText,
      "email" -> nonEmptyText
    ) {
      (checkbeds, rentlo, renthi, autocomplete, email) =>
      val ilo = rentlo.getOrElse("$0").replaceAll("\\D+", "").toInt
      val ihi = renthi.getOrElse(Query.TooDear).replaceAll("\\D+", "").toInt
      val places = autocomplete.split(",").toSet
      Query(BSONObjectID.generate(), checkbeds.toSet, ilo, ihi, places, DateTime.now(), DateTime.now(), email)
    } { query =>
      val formatter = java.text.NumberFormat.getIntegerInstance()
      Some(
        (
          query.bedrooms.toList,
          Some(s"$$${formatter.format(query.rentlo.toLong)}"),
          Some(s"$$${formatter.format(query.renthi.toLong)}"),
          query.places.mkString(","),
          query.email
        ))
    }).fill(Query(BSONObjectID.generate(), Set(0), 500, 4000, Set.empty, new DateTime(), new DateTime(), ""))
}
