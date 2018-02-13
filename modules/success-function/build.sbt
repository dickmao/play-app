name := """success-function"""
shellPrompt in ThisBuild := { state => "[" + Project.extract(state).currentRef.project + "] $ " }

//unmanagedBase := baseDirectory.value / ".." / ".."
unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / ".." / "conf"
scalacOptions ++= Seq(
  "-Ywarn-unused"
)
//libraryDependencies ++= Seq(
//"com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",  
//"org.slf4j" % "slf4j-nop" % "1.7.25"
//)
