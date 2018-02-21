package success_function

import org.joda.time.DateTime
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

import com.github.nscala_time.time.Imports.DateTimeOrdering
import com.redis._
import courier._
import courier.Defaults._
import geocode._
import grizzled.slf4j.Logging
import models.{FormDTO, Query, User}
import models.Query.DateTimeHandler
import org.joda.time.format.{ ISODateTimeFormat, DateTimeFormat }
import play.api.{Configuration, Environment}
import play.api.inject.guice.GuiceApplicationBuilder
import reactivemongo.api._
import reactivemongo.bson.BSONDocument
import reactivemongo_test.Common._
import scala.util._

object SuccessFunction {
  def popmax(id1: String, id2: String)(implicit rediscp: RedisClientPool) : String = {
    val f1 = rediscp.withClient {
      _.hmget("geoitem." + id1, "featureclass", "featurecode", "population").get
    }
    val f2 = rediscp.withClient {
      _.hmget("geoitem." + id2, "featureclass", "featurecode", "population").get
    }
    return if (f1("population").toInt > f2("population").toInt) id1 else id2
  }

  def successFunction(query: FormDTO)(implicit environment: Environment, configuration: Configuration) = {
    val (small, big) = (Set(0,1), Set(2,3,4,5))
    val bedrooms = Set[Int]() ++ (if (query.bedrooms.contains(0)) small else Set()) ++ (if (query.bedrooms.contains(2)) big else Set())
    implicit val rediscp = new RedisClientPool(configuration.getString("redis.host").getOrElse("redis"),
      configuration.getInt("redis.port").getOrElse(6379))
    val byprice = rediscp.withClient { 
      _.zrangebyscore("item.index.price", query.rentlo.min(query.renthi).toDouble, true, query.renthi.max(query.rentlo).toDouble, true, None).getOrElse(List())
    }
    val bybeds = rediscp.withClient {
      _.zrangebyscore("item.index.bedrooms", bedrooms.min.toDouble, true, bedrooms.max.toDouble, true, None).getOrElse(List())
    }

    val results = scala.collection.mutable.Set.empty[String]
    for (place <- query.places.map(_.toLowerCase)) {
      val matches = rediscp.withClient {
        _.zrangebylex("geoitem.index.name", "[%s".format(place), "(%s{".format(place), None).getOrElse(List[String]())
      }
      val geonameids = rediscp.withClient {
        client => {
          matches.flatMap(mat => client.smembers("georitem." + mat.split(":")(1)).getOrElse(Set())).flatten
        }
      }
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
            // proximate.foreach(p1 => {
            //   val geoid = nyp.nearestPlace(p1.coords.get._2.toDouble, p1.coords.get._1.toDouble).id
            //   Logger.debug("geoitem.%s %s %s".format(geoid, client.hget("geoitem." + geoid, "admin2code").getOrElse(""), client.hget("geoitem." + geoid, "name")))
            // })
            proximate.filter(p1 => p0_fields("admin2code") == client.hget("geoitem." + nyp.nearestPlace(p1.coords.get._2.toDouble, p1.coords.get._1.toDouble).id, "admin2code").getOrElse(""))
          }
        }
        results ++= byprice.toSet.intersect(bybeds.toSet).intersect(proximate_and_colocal.map(x => x.member.get).toSet)
      }
    }

    rediscp.withClient {
      client => { results.toList.map(x => client.hgetall1("item." + x).get).sortBy(x => ISODateTimeFormat.dateTimeParser().parseDateTime(x("posted")))(DateTimeOrdering.reverse) }
    }
  }
}


object Main extends App with Logging {
  val injector = new GuiceApplicationBuilder().injector
  implicit val environment = injector.instanceOf[Environment]
  implicit val configuration = injector.instanceOf[Configuration]

  lazy val collection = db("users")
  // it.collect[List](-1, Cursor.FailOnError[List[Query]]()).map { println }

  val uqis =
    Await.result(collection.find(BSONDocument()).cursor[User]().collect[List](-1, Cursor.FailOnError[List[User]]()).map {
      _.flatMap(user => Map(user ->
        user.queries.flatMap(query => Map(query ->
          SuccessFunction
            .successFunction(FormDTO(query.bedrooms, query.rentlo, query.renthi, query.places, user.email))
            .flatMap(item => if (ISODateTimeFormat.dateTimeParser().parseDateTime(item("posted")).isAfter(query.lastEmailed)) Some(item) else None)))))
    }, 2 seconds)

  val format_item = (item: Map[String, String]) => {
    val price = item.get("price").fold("unspecified")(x => java.text.NumberFormat.getIntegerInstance.format(x.toFloat.toLong))
    val ltrim = (s: String) => s.replaceAll("^\\s+", "")
    s"${price} ${ltrim(item.get("desc").getOrElse("")).split("\\s+").take(50).mkString(" ")} ${item("link")}"
  }

  val mailer = Mailer("email-smtp.us-east-1.amazonaws.com", 587)
    .auth(true)
    .as("AKIAIAXTT333AZKZSUSA", "Ao3UcJ5y8Av9sdEFLr0xIZGyDerwc8nCWvgMII8DkjMQ")
    .startTtls(true)()

  for ( (u, qis) <- uqis) {
    for ( ((q, is), j) <- qis.zipWithIndex) {
      if (!is.isEmpty) {
        val formatted = is.map(format_item(_)).mkString("\n\n")
        val f = mailer(Envelope.from("noreply" `@` "shunyet.com")
          .to(u.email.addr)
          .cc("success" `@` "simulator.amazonses.com")
          .subject(s"digest ${DateTimeFormat.forPattern("yyyyMMdd").print(DateTime.now)}")
          .content(Text(formatted))) andThen {
          case Success(v) =>
            val posted = is.map(item => ISODateTimeFormat.dateTimeParser().parseDateTime(item("posted"))).max
            Await.ready(collection.update(BSONDocument("email" -> u.email),
              BSONDocument("$set" -> BSONDocument(s"queries.$j.lastEmailed" -> posted))).map {
              lastError => logger.debug(s"${q.id} posted ${posted}: $lastError")
            }, 5 seconds)
          case Failure(e) =>
            println(e)
        }
        Await.ready(f, Duration.Inf)
      }
    }
  }



  // val it = collection.find(BSONDocument("email" -> "rchiang@cs.stonybrook.edu"), BSONDocument("queries" -> 1)).requireOne[BSONDocument]
  // it.map(bson => bson.getAs[List[Query]]("queries").map { println })

  //collection.count(None).map { println(_) }
  close()
}

