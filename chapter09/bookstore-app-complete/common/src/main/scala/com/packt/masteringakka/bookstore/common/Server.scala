package com.packt.masteringakka.bookstore.common
import akka.actor._
import com.typesafe.config.ConfigFactory
import collection.JavaConversions._
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.Http
import com.typesafe.conductr.bundlelib.akka.Env
import com.typesafe.conductr.lib.akka.ConnectionContext
import com.typesafe.conductr.bundlelib.akka.StatusService

/**
 * Server class that each module can use to setup its actor system and http server/endpoints
 */
class Server(boot:Bootstrap, service:String){
  import akka.http.scaladsl.server.Directives._
  val config = Env.asConfig
  val systemName = sys.env.getOrElse("BUNDLE_SYSTEM", "BookstoreSystem")
  val systemVersion = sys.env.getOrElse("BUNDLE_SYSTEM_VERSION", "1")
  implicit val system = ActorSystem(s"$systemName-$systemVersion", config.withFallback(ConfigFactory.load()))  
  implicit val mater = ActorMaterializer()
  import system.dispatcher

  //Make sure projection storage system is initialized
  val projExt = CassandraProjectionStorage(system)
  while(!projExt.isInitialized){
    println("Waiting for resumable projection system to be initialized")
    Thread.sleep(1000)
  }

  //Boot up each service module from the config and get the routes
  val routes = boot.bootup(system).map(_.routes)   
  val definedRoutes = routes.reduce(_~_)
  val finalRoutes = pathPrefix("api")(definedRoutes ) 
  
  val serviceConf = system.settings.config.getConfig(service)
  val serverSource =
    Http().bind(interface = serviceConf.getString("ip"), port = serviceConf.getInt("port")) 
  val log = Logging(system.eventStream, "Server")
  log.info("Starting up on port {} and ip {}", serviceConf.getString("port"), serviceConf.getString("ip"))  
  
  val sink = Sink.foreach[Http.IncomingConnection](_.handleWith(finalRoutes))
  serverSource.to(sink).run  
  
  implicit val cc = ConnectionContext()
  StatusService.signalStartedOrExit() 
}

