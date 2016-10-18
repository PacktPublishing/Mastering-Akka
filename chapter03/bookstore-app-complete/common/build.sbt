name := "chapter3-bookstore-common-complete"

libraryDependencies ++= { 
    val akkaVersion = "2.4.7"
    val slickVersion = "3.1.1"
    val hikariCPVersion = "2.4.6"
    Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "com.typesafe.slick" %% "slick" % slickVersion,
    "com.typesafe.slick" %% "slick-hikaricp" % slickVersion exclude("com.zaxxer", "HikariCP-java6"),
    "com.zaxxer" % "HikariCP" % hikariCPVersion,
    "net.databinder" %% "unfiltered-filter" % "0.8.4",
    "net.databinder" %% "unfiltered-netty" % "0.8.4",
    "net.databinder" %% "unfiltered-netty-server" % "0.8.4",
    "net.databinder" %% "unfiltered-json4s" % "0.8.4",
    "org.json4s" %% "json4s-ext" % "3.2.9",
    "org.postgresql" % "postgresql" % "9.4.1208.jre7",
    "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
  )
}