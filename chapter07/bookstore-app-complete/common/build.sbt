name := "chapter7-bookstore-common-complete"

libraryDependencies ++= {
  val akkaVersion = "2.4.8"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.14",
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % akkaVersion,    
    "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,  
    "ch.qos.logback" % "logback-classic" % "1.0.9",
    "org.json4s" %% "json4s-ext" % "3.2.9",
    "org.json4s" %% "json4s-native" % "3.2.9",
    "com.google.protobuf" % "protobuf-java"  % "2.5.0",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion % Test
  )
}