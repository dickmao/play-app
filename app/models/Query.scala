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
lastEmailed: DateTime
)

object Query {
  import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  implicit val QueryFormat: OFormat[Query] = (
    (__ \ "_id").format[BSONObjectID] and
      (__ \ "bedrooms").format[Set[Int]] and
      (__ \ "rentlo").format[Int] and
      (__ \ "renthi").format[Int] and
      (__ \ "places").format[Set[String]] and
      (__ \ "createdAt").format[DateTime] and
      (__ \ "lastEmailed").format[DateTime]
  )(Query.apply, unlift(Query.unapply))
}
