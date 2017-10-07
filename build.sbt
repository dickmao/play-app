name := """play-scala-forms-example"""

val vversion = "2.5.x"
version := vversion
scalaVersion := "2.11.11"

lazy val gitRepo = s"git:file:///home/dick/playframework/#$vversion" 
lazy val g = RootProject(uri(gitRepo))

libraryDependencies += filters

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % "test"

libraryDependencies += "com.typesafe.play.modules" %% "play-modules-redis" % "2.5.0"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
