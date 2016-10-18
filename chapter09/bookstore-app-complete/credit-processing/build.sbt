import ByteConversions._

name := "chapter9-credit-processing"

organization := "com.packt.masteringakka"

version := "0.1.0"

scalaVersion := "2.11.2"

libraryDependencies ++= {  
  Seq(
    "com.packt.masteringakka" %% "chapter9-bookstore-common" % "0.1.0-SNAPSHOT"
  )
}

normalizedName in Bundle := "credit"

BundleKeys.system := "CreditSystem"

javaOptions in Universal := Seq(
  "-J-Xmx128m",
  "-J-Xms128m"
)

BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 256.MiB
BundleKeys.diskSpace := 50.MB

BundleKeys.endpoints := Map(
  "akka-remote" -> Endpoint("tcp"),
  "credit-processing" -> Endpoint("http", 0, Set(URI("http://:9000/credit-processing")))
)

BundleKeys.startCommand += "-main com.packt.masteringakka.bookstore.credit.Main"

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)