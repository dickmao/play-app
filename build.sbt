name := """play-app"""

scalaVersion := "2.11.11"

// removes all jar mappings in universal and appends the fat jar
// universalMappings: Seq[(File,String)]
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

  // Since play uses separate pidfile we have to provide it with a proper path
  // name of the pid file must be play.pid
  s"-Dpidfile.path=/var/run/${packageName.value}/play.pid",

  // alternative, you can remove the PID file
  // s"-Dpidfile.path=/dev/null",

  // Use separate configuration file for production environment
  s"-Dconfig.file=/usr/share/${packageName.value}/conf/production.conf",

  // Use separate logger configuration file for production environment
  s"-Dlogger.file=/usr/share/${packageName.value}/conf/production-logger.xml",

  // You may also want to include this setting if you use play evolutions
  "-DapplyEvolutions.default=true"
)
