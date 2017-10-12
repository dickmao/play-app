name := """play-scala-forms-example"""

val vversion = "2.5.x"
version := vversion
scalaVersion := "2.11.11"

lazy val gitRepo = s"git:file:///home/dick/playframework/#$vversion" 
lazy val g = RootProject(uri(gitRepo))

libraryDependencies ++= Seq(
  filters,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % "test",
  "com.typesafe.play.modules" %% "play-modules-redis" % "2.5.0",
  "org.scalactic" %% "scalactic" % "3.0.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "net.debasishg" %% "redisclient" % "3.4",
  "com.google.maps" % "google-maps-services" % "0.2.4"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

initialCommands := """
|import com.google.maps._
|import com.redis._
|val context = new GeoApiContext.Builder().apiKey("AIzaSyD5GTvU9JpZKsh9XyAVKNVNdUlo5BZnXNQ").build()
|""".stripMargin

