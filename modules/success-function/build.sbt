name := """success-function"""
shellPrompt in ThisBuild := { state => "[" + Project.extract(state).currentRef.project + "] $ " }

//unmanagedBase := baseDirectory.value / ".." / ".."
unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / ".." / "conf"
unmanagedResourceDirectories in Compile += baseDirectory.value / "conf"
scalacOptions ++= Seq(
  "-Ywarn-unused"
)

resolvers += Resolver.bintrayRepo("lightshed", "maven")

libraryDependencies ++= Seq(
  "ch.lightshed" %% "courier" % "0.1.4",
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "org.slf4j" % "slf4j-simple" % "1.7.25",
  "org.clapper" %% "grizzled-slf4j" % "1.3.1",
  "com.sun.mail" % "javax.mail" % "1.6.1"
)

