package reactivemongo_test

import reactivemongo.api.MongoConnection
import scala.concurrent.{ Await, ExecutionContext, Future }
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import reactivemongo.api.{
  CrAuthentication,
  FailoverStrategy,
  MongoDriver,
  MongoConnectionOptions
}
import play.api.{Configuration, Environment}

object Common {

  def apply(environment: Environment, configuration: Configuration) =
    new Common(environment, configuration)

  final class Common private[reactivemongo_test](environment: Environment, configuration: Configuration) {
    val crMode = Option(System getProperty "test.authMode").
      filter(_ == "cr").map(_ => CrAuthentication)

    val failoverRetries = Option(System getProperty "test.failoverRetries").
      flatMap(r => scala.util.Try(r.toInt).toOption).getOrElse(7)

    val failoverStrategy = FailoverStrategy(retries = failoverRetries)
    val DefaultOptions = {
      val a = MongoConnectionOptions(
        failoverStrategy = failoverStrategy,
        nbChannelsPerNode = 20)

      val b = {
        if (Option(System getProperty "test.enableSSL").exists(_ == "true")) {
          a.copy(sslEnabled = true, sslAllowsInvalidCert = true)
        } else a
      }

      crMode.fold(b) { mode => b.copy(authMode = mode) }
    }

    private val timeoutFactor = 1.25D
    def estTimeout(fos: FailoverStrategy): FiniteDuration =
      (1 to fos.retries).foldLeft(fos.initialDelay) { (d, i) =>
        d + (fos.initialDelay * ((timeoutFactor * fos.delayFactor(i)).toLong))
      }

    val timeout: FiniteDuration = {
      val maxTimeout = estTimeout(failoverStrategy)

      if (maxTimeout < 10.seconds) 10.seconds
      else maxTimeout
    }

    val host = configuration.getString("mongodb.host").getOrElse("localhost")
    val uri = configuration.getString("mongodb.uri").getOrElse(s"mongodb://${host}:27017/keeyosk")
    lazy val driver = new MongoDriver
    val connection = driver.connection(List(host), DefaultOptions)

    lazy val database = for {
      uri <- Future.fromTry(MongoConnection.parseURI(uri))
      con = driver.connection(uri)
      dn <- Future(uri.db.get)
      db <- con.database(dn)
    } yield db

    def db(coll: String) = Await.result(database.map(_.collection(coll)), timeout)

    database.onComplete {
      case resolution =>
        println(s"DB resolution: $resolution")
    }

    def close(): Unit = {
      val logger = reactivemongo.util.LazyLogger("Common")
      try {
        driver.close()
      } catch {
        case e: Throwable =>
          logger.warn(s"Fails to stop the default driver: $e")
          logger.debug("Fails to stop the default driver", e)
      }
    }


  }
}
