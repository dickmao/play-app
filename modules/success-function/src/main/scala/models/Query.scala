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
  import reactivemongo.bson._

  implicit val JsonQueryFormat: OFormat[Query] = (
    (__ \ "_id").format[BSONObjectID] and
      (__ \ "bedrooms").format[Set[Int]] and
      (__ \ "rentlo").format[Int] and
      (__ \ "renthi").format[Int] and
      (__ \ "places").format[Set[String]] and
      (__ \ "createdAt").format[DateTime] and
      (__ \ "lastEmailed").format[DateTime]
  )(Query.apply, unlift(Query.unapply))

  // I believe reactivemongo writes java.util.Date as BSONDateTime
  // and joda DateTimes as NumberLong
  implicit object DateTimeHandler extends BSONHandler[BSONLong, DateTime] {
    def read(time: BSONLong) = new DateTime(time.value)
    def write(jdtime: DateTime) = BSONLong(jdtime.getMillis)
  }

  implicit object QueryReader extends BSONDocumentReader[Query] {
    def read(bson: BSONDocument): Query = {
      val opt: Option[Query] = for {
        id <- bson.getAs[BSONObjectID]("_id")
        bedrooms <- bson.getAs[List[Int]]("bedrooms")
        rentlo <- bson.getAs[Int]("rentlo")
        renthi <- bson.getAs[Int]("renthi")
        places <- bson.getAs[List[String]]("places")
        createdAt <- bson.getAs[DateTime]("createdAt")
        lastEmailed <- bson.getAs[DateTime]("lastEmailed")
      } yield new Query(id, bedrooms.toSet, rentlo, renthi, places.toSet, createdAt, lastEmailed)
      opt.get
    }
  }
}

