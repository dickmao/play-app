name := """play-app"""

scalaVersion := "2.11.11"
dockerRepository := Some("303634175659.dkr.ecr.us-east-2.amazonaws.com")
dockerExposedPorts := Seq(9000)

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
  Process(Seq("docker", "rmi", (dockerTarget in Docker).value)) ! new ProcessLogger {
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

publishLocal in Docker := {
  val _ = (playStageSecret.value, removeOldImage.value)
  (publishLocal in Docker).value
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
  "com.github.nscala-time" %% "nscala-time" % "2.16.0"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)



initialCommands := """
|import com.redis._
|import geocode._
|val redisClient = new RedisClient("localhost", 6379)
|val rgc = new ReverseGeoCode(new java.io.FileInputStream("/home/dick/scrapy/NY.tsv"), true)
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
