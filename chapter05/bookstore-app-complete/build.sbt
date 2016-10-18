import NativePackagerHelper._

name := "chapter5-bookstore-app-complete"

lazy val commonSettings = Seq(
  organization := "com.packt.masteringakka",
  version := "0.1.0",
  scalaVersion := "2.11.8"
)

lazy val root = (project in file(".")).
  aggregate(common, inventoryMgmt, userMgmt, creditProcessing, salesOrderProcessing, server)

lazy val common = (project in file("common")).
  settings(commonSettings: _*)

lazy val inventoryMgmt = (project in file("inventory-management")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val userMgmt = (project in file("user-management")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val creditProcessing = (project in file("credit-processing")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val salesOrderProcessing = (project in file("sales-order-processing")).
  settings(commonSettings: _*).
  dependsOn(common, inventoryMgmt, userMgmt, creditProcessing)      

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
  )
  .dependsOn(common, inventoryMgmt, userMgmt, creditProcessing, salesOrderProcessing)
  .enablePlugins(JavaAppPackaging)
}