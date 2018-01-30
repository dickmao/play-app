package models

import reactivemongo.bson.BSONObjectID

case class User(
id: BSONObjectID,
email: String,
queries: List[Query]
)

object User {
  import play.api.libs.json._
  import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
  import play.api.libs.functional.syntax._

  implicit val UserFormat: OFormat[User] = (
    (__ \ "_id").format[BSONObjectID] and
      (__ \ "email").format[String](Reads.email) and
      (__ \ "queries").format[List[Query]](Reads.minLength[List[Query]](1))
  )(User.apply, unlift(User.unapply))
}
