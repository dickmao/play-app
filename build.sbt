import play.sbt.routes.RoutesKeys
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker._

name := """play-app"""
lazy val playStageSecret = taskKey[Unit]("Runs playGenerateSecret and puts output in conf as production.conf")
lazy val removeOldImage = taskKey[Unit]("Remove old image to avoid danglers")
lazy val reloginEcr = taskKey[Unit]("Renew ECR Authorization Token")

lazy val settings = Seq(
  // logLevel := Level.Debug,
  // git.formattedShaVersion := { (git.gitHeadCommit in ThisBuild).value.map(_.substring(0, 6)) },
  version in Docker := git.gitCurrentBranch.value,
  dockerRepository := Some("303634175659.dkr.ecr.us-east-2.amazonaws.com"),
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
  },
  reloginEcr := {
    Keys.streams.value.log.info("Renewing ECR Authorization Token")
    Process(Seq("aws", "ecr", "get-login", "--no-include-email", "--region", sys.props.getOrElse("AWS_REGION", default = "us-east-2"))) ! new ProcessLogger {
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
)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(Common.settings: _*)
  .settings(
    settings,
    libraryDependencies ++= Dependencies.commonDependencies,
    libraryDependencies += filters,
    RoutesKeys.routesImport += "play.modules.reactivemongo.PathBindables._",
    NativePackagerKeys.dockerExposedPorts := Seq(9000),
    excludeDependencies += "org.slf4j" % "slf4j-simple",
    playStageSecret := {
      val file = baseDirectory.value / "conf" / "production.conf"
      import play.sbt.PlayImport._
      val secret = PlayKeys.generateSecret.value
      IO.write(file, s"""include "application.conf"\nplay.crypto.secret="$secret"\n""")
      mappings in Universal ++= directory(file)
    },
    publish in Docker := {
      // val _ = (removeOldImage.value, reloginEcr.value)
      // Mark Harrah 20131010 Because of a bug in scala, you have to use dummy names
      // otherwise you could just use val_ =
      val _ = (reloginEcr.value, playStageSecret.value)
      (publish in Docker).value
    }
  )
  .aggregate(successFunction)
  .dependsOn(successFunction)

lazy val successFunction = (project in file("modules/success-function"))
  .enablePlugins(JavaAppPackaging)
  .settings(Common.settings: _*)
  .settings(
    settings,
    libraryDependencies ++= Dependencies.commonDependencies,
    libraryDependencies += filters,
    mappings in Universal ++= directory(baseDirectory.value / "src" / "main" / "resources"),
    mappings in Universal ++= directory(baseDirectory.value / ".." / ".." / "conf"),
    publish in Docker := {
      // val _ = (removeOldImage.value, reloginEcr.value)
      // Mark Harrah 20131010 Because of a bug in scala, you have to use dummy names
      // otherwise you could just use val_ =
      reloginEcr.value
      (publish in Docker).value
    },
    dockerCommands := Seq(
      Cmd("FROM", "java:latest"),
      Cmd("ADD", "opt /opt"),
      Cmd("WORKDIR", "/opt/docker"),
//      ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
      ExecCmd("RUN", "apt-get", "-yq", "update"),
      ExecCmd("RUN", "apt-get", "-yq", "install", "cron"),
//      Cmd("USER", "daemon"),
      ExecCmd("RUN", "crontab", "resources/cron-success-function"),
      ExecCmd("ENTRYPOINT", "cron", "-f"),
      ExecCmd("CMD")
    )
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
