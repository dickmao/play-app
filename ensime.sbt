// to generate 3.0 server-jars:
// comment out ensimeRepositoryUrls in global.sbt in ~/.sbt/1.0
// and uncomment this

ensimeRepositoryUrls in ThisBuild += "https://oss.sonatype.org/content/repositories/snapshots/"
//ensimeServerVersion in ThisBuild := "3.0.0-SNAPSHOT"
//ensimeProjectServerVersion in ThisBuild := "3.0.0-SNAPSHOT"

