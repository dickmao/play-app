package success_function

import org.joda.time.DateTime
import play.api.Logger
import scala.concurrent._
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
import reactivemongo_test.Common
import scala.util.{ Failure, Success, _ }

object SuccessFunction {
  def popmax(id1: String, id2: String)(implicit rediscp: RedisClientPool) : Future[String] = {
    val f1 = Future { 
      rediscp.withClient {
        _.hmget("geoitem." + id1, "featureclass", "featurecode", "population").get
      } 
    }
    val f2 = Future { 
      rediscp.withClient {
        _.hmget("geoitem." + id2, "featureclass", "featurecode", "population").get
      }
    }

    val result = for {
      a <- f1
      b <- f2
    } yield if (a("population").toInt > b("population").toInt) id1 else id2
    return result
  }

  def successFunction(query: FormDTO)(implicit environment: Environment, configuration: Configuration): Future[List[Map[String, String]]] = {
    val (small, big) = (Set(0,1), Set(2,3,4,5))
    val bedrooms = Set[Int]() ++ (if (query.bedrooms.contains(0)) small else Set()) ++ (if (query.bedrooms.contains(2)) big else Set())
    implicit val rediscp = new RedisClientPool(configuration.getString("redis.host").getOrElse("redis"), configuration.getInt("redis.port").getOrElse(6379), 8, configuration.getInt("redis.database").getOrElse(0))
    lazy val popget = ((id2: String) => {
      Future {
        rediscp.withClient {
          _.hmget("geoitem." + id2, "featureclass", "featurecode", "population")
            .get("population").toInt
        }
      }
    })

    val fitems = query.places.map{_.toLowerCase}.map{ place =>
      for {
        matches <- Future {
          rediscp.withClient {
            _.zrangebylex("geoitem.index.name", "[%s".format(place), "(%s{".format(place), None).getOrElse(List[String]())
          }
        }
        geonameids <- Future {
          rediscp.withClient {
            client => {
              matches.flatMap(mat => client.smembers("georitem." + mat.split(":")(1)).getOrElse(Set())).flatten
            }
          }
        }
        i0 <- Future.sequence(geonameids.map(s => popget(s))).transform(l => 
          l.zipWithIndex.maxBy(_._1)._2, t => t)
        p0_fields <- Future {
          rediscp.withClient {
            _.hmget("geoitem." + geonameids(i0), "longitude", "latitude", "admin2code", "featurecode").get
          }
        }
        dist = if (p0_fields("featurecode").matches("PPLA.*")) 25 else 1.5
        proximate <- Future {
          rediscp.withClient {
            _.georadius("item.geohash.coords", p0_fields("longitude"), p0_fields("latitude"), dist, "km", true, false, false, None, None, None, None).getOrElse(List()).flatten
          }
        }
        byprice <- Future {
          rediscp.withClient {
            _.zrangebyscore("item.index.price", query.rentlo.min(query.renthi).toDouble, true, query.renthi.max(query.rentlo).toDouble, true, None).getOrElse(List())
          }
        }
        bybeds <- Future {
          rediscp.withClient {
            _.zrangebyscore("item.index.bedrooms", bedrooms.min.toDouble, true, bedrooms.max.toDouble, true, None).getOrElse(List())
          }
        }
        nyp = new ReverseGeoCode(environment.resourceAsStream(configuration.getString("reverse_geocode").getOrElse("NY.icare.tsv")).get, true)
        proximate_and_colocal <- Future {
          rediscp.withClient {
            client => {
              // proximate.foreach(p1 => {
              //   val geoid = nyp.nearestPlace(p1.coords.get._2.toDouble, p1.coords.get._1.toDouble).id
              //   Logger.debug("geoitem.%s %s %s".format(geoid, client.hget("geoitem." + geoid, "admin2code").getOrElse(""), client.hget("geoitem." + geoid, "name")))
              // })
              proximate.filter(p1 => p0_fields("admin2code") == client.hget("geoitem." + nyp.nearestPlace(p1.coords.get._2.toDouble, p1.coords.get._1.toDouble).id, "admin2code").getOrElse(""))
            }
          }
        }
        result = byprice.toSet.intersect(bybeds.toSet).intersect(proximate_and_colocal.map(x => x.member.get).toSet)
      } yield result
    }

    val flos = for {
      // https://stackoverflow.com/questions/20874186/scala-listfuture-to-futurelist-disregarding-failed-futures
      ssos <- Future.sequence(fitems.map(f => f.map(Success(_)).recover({ case e => Failure(e) }))).map(_.collect({ case Success(x) => x }))
      los = ssos.flatten.toList
    } yield los

    val flfm = flos.map(los => los.map(item => Future {
      rediscp.withClient {
        client => client.hgetall1("item." + item).get
      }
    }))

    flfm.flatMap(lfm => Future.sequence(lfm))
  }


}

object Main extends App with Logging {
  val injector = new GuiceApplicationBuilder().injector
  implicit val environment = injector.instanceOf[Environment]
  implicit val configuration = injector.instanceOf[Configuration]

  val common = Common(environment, configuration)
  lazy val collection = common.db("users")
  // it.collect[List](-1, Cursor.FailOnError[List[Query]]()).map { println }

  val uqis =
    Await.result(collection.find(BSONDocument()).cursor[User]().collect[List](-1, Cursor.FailOnError[List[User]]()).map {
      _.flatMap(user =>
        Map(user -> user.queries.flatMap(query => Map(query ->
          Await.result(SuccessFunction
            .successFunction(FormDTO(query.bedrooms, query.rentlo, query.renthi, query.places))
            .map {
              _.flatMap(item => if (ISODateTimeFormat.dateTimeParser().parseDateTime(item("posted")).isAfter(query.lastEmailed)) Some(item) else None)
            }, 20 seconds)))))
    }, 20 seconds)

  val format_item = (item: Map[String, String]) => {
    val price = item.get("price").fold("unspecified")(x => java.text.NumberFormat.getIntegerInstance.format(x.toFloat.toLong))
    val ltrim = (s: String) => s.replaceAll("^\\s+", "")
    s"${price} ${ltrim(item.get("desc").getOrElse("")).split("\\s+").take(50).mkString(" ")} ${item("link")}"
  }

  // logger.debug(s"${configuration.getString("ses.user").getOrElse("")} ${configuration.getString("ses.password").getOrElse("")}")
  val mailer = Mailer("email-smtp.us-east-1.amazonaws.com", 587)
    .auth(true)
    .as(configuration.getString("ses.user").getOrElse(""), configuration.getString("ses.password").getOrElse(""))
    .startTtls(true)()

  for ( (u, qis) <- uqis) {
    for ( ((q, is), j) <- qis.zipWithIndex) {
      if (!is.isEmpty) {
        val formatted = is.map(format_item(_)).mkString("\n\n")
        val subdomain = configuration.getString("subdomain")
        val manageAt = if (subdomain.exists(_.trim.nonEmpty)) s"Manage searches http://${subdomain.get}/mongo/getUid/${u.id.stringify}" else ""
        val f = mailer(Envelope.from("rchiang" `@` "cs.stonybrook.edu")
          .to(u.email.addr)
          .cc("success" `@` "simulator.amazonses.com")
          .subject(s"digest ${DateTimeFormat.forPattern("yyyyMMdd").print(DateTime.now)}")
          .content(Text(formatted + "\n\n" + manageAt))) andThen {
          case Success(v) =>
            val posted = is.map(item => ISODateTimeFormat.dateTimeParser().parseDateTime(item("posted"))).max
            Await.ready(collection.update(BSONDocument("email" -> u.email),
              BSONDocument("$set" -> BSONDocument(s"queries.$j.lastEmailed" -> posted))).map {
              lastError => {
                logger.info(s"${q.id} posted ${q.lastEmailed} -> ${posted}: $lastError")
              }
            }, 25 seconds)
          case Failure(e) =>
            println(e)
            throw(e)
        }
        Await.ready(f, Duration.Inf)
      }
    }
  }

  // val it = collection.find(BSONDocument("email" -> "rchiang@cs.stonybrook.edu"), BSONDocument("queries" -> 1)).requireOne[BSONDocument]
  // it.map(bson => bson.getAs[List[Query]]("queries").map { println })

  //collection.count(None).map { println(_) }
  common.close()
}

