

name := "initial-example-app"

lazy val commonSettings = Seq(
  organization := "com.packt.masteringakka",
  version := "0.1.0",
  scalaVersion := "2.11.2"
)

lazy val root = (project in file(".")).
  aggregate(common, bookServices, userServices, creditServices, orderServices, server)

lazy val common = (project in file("common")).
  settings(commonSettings: _*)

lazy val bookServices = (project in file("book-services")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val userServices = (project in file("user-services")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val creditServices = (project in file("credit-services")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val orderServices = (project in file("order-services")).
  settings(commonSettings: _*).
  dependsOn(common)

//  packageArchetype.java_server
lazy val server = {
  import com.typesafe.sbt.packager.docker._
  Project(
    id = "server",
    base = file("server"),
    settings = commonSettings ++ Seq(
      mainClass in Compile := Some("com.packt.masteringakka.bookstore.server.Server"),
      dockerCommands := dockerCommands.value.filterNot {
        // ExecCmd is a case class, and args is a varargs variable, so you need to bind it with @
        case Cmd("USER", args@_*) => true
        // dont filter the rest
        case cmd => false
      },
      version in Docker := "latest",
      dockerExposedPorts := Seq(8080),
      maintainer in Docker := "mastering-akka@packt.com",
      dockerBaseImage := "java:8"
    )
  ).dependsOn(common, bookServices, userServices, creditServices, orderServices)
    .enablePlugins(JavaAppPackaging)
}