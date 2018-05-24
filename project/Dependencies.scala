import sbt._
import Keys._

object Dependencies {
  val commonDependencies: Seq[ModuleID] = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % "test",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "net.debasishg" %% "redisclient" % "3.4",
    "OfflineReverseGeocode" % "OfflineReverseGeocode" % "1.0-SNAPSHOT",
    "com.github.nscala-time" %% "nscala-time" % "2.16.0",
    "commons-lang" % "commons-lang" % "2.6",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.13.0-play25",
    "io.swagger" %% "swagger-play2" % "1.5.3"
  )
}
