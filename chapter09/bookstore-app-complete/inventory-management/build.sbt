import ByteConversions._

name := "chapter9-inventory-management"
organization := "com.packt.masteringakka"
version := "0.1.0"

scalaVersion := "2.11.2"

libraryDependencies ++= {  
  Seq(
    "com.packt.masteringakka" %% "chapter9-bookstore-common" % "0.1.0-SNAPSHOT"
  )
}

normalizedName in Bundle := "inventory"

BundleKeys.system := "InventorySystem"

javaOptions in Universal := Seq(
  "-J-Xmx128m",
  "-J-Xms128m"
)

BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 256.MiB
BundleKeys.diskSpace := 50.MB

BundleKeys.endpoints := Map(
  "akka-remote" -> Endpoint("tcp"),
  "inventory-management" -> Endpoint("http", 0, Set(URI("http://:9000/inventory-management")))
)

BundleKeys.startCommand += "-main com.packt.masteringakka.bookstore.inventory.Main"

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)