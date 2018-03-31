import sbt._
import Keys._
import com.typesafe.sbt.SbtNativePackager.Universal

object Common {

  lazy val settings: Seq[Def.Setting[_]] = Seq(
    scalaVersion := "2.11.11",
    resolvers += Resolver.mavenLocal,
    resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
    resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases",
    // removes doc mappings
    mappings in Universal := (mappings in Universal).value filter {
      case (file, name) =>  ! name.startsWith("share/doc")
    }
  )

}
