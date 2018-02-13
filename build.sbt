name := """play-app"""

lazy val commonSettings = Seq(
  scalaVersion := "2.11.11",
  resolvers += Resolver.mavenLocal,
  resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  libraryDependencies ++= Seq(
    filters,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % "test",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "net.debasishg" %% "redisclient" % "3.4",
    "OfflineReverseGeocode" % "OfflineReverseGeocode" % "1.0-SNAPSHOT",
    "com.github.nscala-time" %% "nscala-time" % "2.16.0",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.12.6-play25"
  )
)
dockerRepository := Some("303634175659.dkr.ecr.us-east-2.amazonaws.com")
dockerExposedPorts := Seq(9000)

import play.sbt.routes.RoutesKeys
RoutesKeys.routesImport += "play.modules.reactivemongo.PathBindables._"

lazy val playStageSecret = taskKey[Unit]("Runs playGenerateSecret and puts output in conf as production.conf")

playStageSecret := {
  import play.sbt.PlayImport._
  val result = PlayKeys.generateSecret.value
  val file = baseDirectory.value / "conf" / "production.conf"
  IO.write(file, s"""include "application.conf"\nplay.crypto.secret="$result"\n""")
}

lazy val removeOldImage = taskKey[Unit]("Remove old image to avoid danglers")

removeOldImage := {
  Keys.streams.value.log.info("Removing old " + (dockerTarget in Docker).value)
  Process(Seq("docker", "rmi", "--force", (dockerTarget in Docker).value)) ! new ProcessLogger {
    def error(err: => String) = err match {
      case s if s.contains("No such image") => Keys.streams.value.log.info("Pristine")
      case s                                =>
    }
    def info(inf: => String) = inf match {
      case s                                =>
    }
    def buffer[T](f: => T) = f
  }
}

lazy val reloginEcr = taskKey[Unit]("Renew ECR Authorization Token")

reloginEcr := {
  Keys.streams.value.log.info("Renewing ECR Authorization Token")
  Process(Seq("aws", "ecr", "get-login", "--no-include-email")) ! new ProcessLogger {
    def error(err: => String) = err match {
      case s if !s.trim.isEmpty => Keys.streams.value.log.error(s)
      case s                    =>
    }
    def info(inf: => String) = inf match {
      case s if s.contains("login")  => Process(s) ! new ProcessLogger {
        def error(err: => String) = err match {
          case s                     =>
        }
        def info(inf: => String) = inf match {
          case s if s.contains("Login Succeeded") => Keys.streams.value.log.info("ECR login renewed")
          case s                                  => Keys.streams.value.log.warn("ECR login likely failed")
        }
        def buffer[T](f: => T) = f
      }
      case s                         => Keys.streams.value.log.warn("ECR get-login failed")
    }
    def buffer[T](f: => T) = f
  }
}

publishLocal in Docker := {
  val _ = (playStageSecret.value, removeOldImage.value)
  (publishLocal in Docker).value
}

publish in Docker := {
  val _ = (playStageSecret.value, removeOldImage.value, reloginEcr.value)
  (publish in Docker).value
}

// removes doc mappings
mappings in Universal := (mappings in Universal).value filter {
  case (file, name) =>  ! name.startsWith("share/doc")
}

lazy val aaaMain = (project in file("."))
  .settings(commonSettings)
  .enablePlugins(PlayScala)
  .aggregate(successFunction)
  .dependsOn(successFunction)

lazy val successFunction = (project in file("modules/success-function"))
  .settings(commonSettings)
  .settings(
    publish in Docker := {}
  )

initialCommands in console := """
|import scala.concurrent.Future
|import scala.language.postfixOps
|import javax.inject.Inject
|import models._
|import org.joda.time.DateTime
|import play.api.Logger
|import play.api.data.Form
|import play.api.i18n._
|import play.api.libs.concurrent.Execution.Implicits.defaultContext
|import play.api.libs.functional.syntax._
|import play.api.libs.json._
|import play.api.mvc._
|import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
|import reactivemongo.api.{Cursor, ReadPreference, MongoConnection}
|import reactivemongo.play.json._
|import reactivemongo.play.json.collection._
|val driver1 = new reactivemongo.api.MongoDriver
|val connection3 = driver1.connection(List("localhost"))
|def dbFromConnection(connection: MongoConnection): Future[JSONCollection] =
|  connection.database("keeyosk").
|    map(_.collection("users"))
|val futcursor = dbFromConnection(connection3).map(_.find(Json.obj("email" -> "alicia.shi@gmail.com")).cursor[User](ReadPreference.primary))
|scala.concurrent.Await.ready(futcursor.flatMap(_.collect[List](-1, Cursor.FailOnError[List[User]]())), scala.concurrent.duration.Duration.Inf).value
""".stripMargin

initialCommands in console := """
|import com.redis._
|import geocode._
|val rediscp = new RedisClientPool("localhost", 6379)
|val redisClient = new RedisClient("localhost", 6379)
|val nyp = new ReverseGeoCode(new java.io.FileInputStream("/home/dick/play-app/conf/NY.P.tsv"), true)
|def time[R](block: => R): R = {
|    val t0 = System.nanoTime()
|    val result = block    // call-by-name
|    val t1 = System.nanoTime()
|    println("Elapsed time: " + (t1 - t0) + "ns")
|    result
|}
""".stripMargin

javaOptions in Universal ++= Seq(
  // JVM memory tuning
  "-J-Xmx1024m",
  "-J-Xms512m",
  s"-Dconfig.file=conf/production.conf",
  // You may also want to include this setting if you use play evolutions
  "-DapplyEvolutions.default=true"
)
