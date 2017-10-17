name := """play-scala-forms-example"""

val vversion = "2.5.x"
version := vversion
scalaVersion := "2.11.11"

lazy val gitRepo = s"git:file:///home/dick/playframework/#$vversion" 
lazy val g = RootProject(uri(gitRepo))
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

