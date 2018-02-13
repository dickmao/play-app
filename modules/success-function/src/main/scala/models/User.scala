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
  import reactivemongo.bson._
  import reactivemongo.bson.BSONArray

  implicit val UserFormat: OFormat[User] = (
    (__ \ "_id").format[BSONObjectID] and
      (__ \ "email").format[String](Reads.email) and
      (__ \ "queries").format[List[Query]]
  )(User.apply, unlift(User.unapply))

  //implicit val UserHandler = Macros.handler[User]
  implicit object UserReader extends BSONDocumentReader[User] {
    def read(bson: BSONDocument): User = {
      val opt: Option[User] = for {
        id <- bson.getAs[BSONObjectID]("_id")
        email <- bson.getAs[String]("email")
        queries <- bson.getAs[List[Query]]("queries")
      } yield new User(id, email, queries)
      opt.get
    }
  }
}
