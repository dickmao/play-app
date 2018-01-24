name := """play-app"""

scalaVersion := "2.11.11"
dockerRepository := Some("303634175659.dkr.ecr.us-east-2.amazonaws.com")
dockerExposedPorts := Seq(9000)

lazy val playStageSecret = taskKey[Unit]("Runs playGenerateSecret and puts output in conf as production.conf")

playStageSecret := {
  import play.sbt.PlayImport._
  val result = PlayKeys.generateSecret.value
  val file = baseDirectory.value / "conf" / "production.conf"
  IO.write(file, s"""include "application.conf"\nmongodb.uri = "mongodb://mongodb:27017/morphia_example"\nplay.crypto.secret="$result"\n""")
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

// generate application secret on every docker:publish
// find docker:publish and append a command to it
// removes doc mappings
mappings in Universal := (mappings in Universal).value filter {
  case (file, name) =>  ! name.startsWith("share/doc")
}



resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  filters,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % "test",
  "com.typesafe.play.modules" %% "play-modules-redis" % "2.5.0",
  "org.scalactic" %% "scalactic" % "3.0.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "net.debasishg" %% "redisclient" % "3.4",
  "com.google.maps" % "google-maps-services" % "0.2.4",
  "OfflineReverseGeocode" % "OfflineReverseGeocode" % "1.0-SNAPSHOT",
  "com.github.nscala-time" %% "nscala-time" % "2.16.0",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.12.6-play25",
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.2.0",
  "org.mongodb.morphia" % "morphia" % "1.3.2"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)



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


// allow BSONObjectID as a first-class type in routes
import play.sbt.routes.RoutesKeys
RoutesKeys.routesImport += "play.modules.reactivemongo.PathBindables._"
