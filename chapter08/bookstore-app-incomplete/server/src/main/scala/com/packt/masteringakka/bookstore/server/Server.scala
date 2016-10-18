package com.packt.masteringakka.bookstore.server
import akka.actor._
import com.typesafe.config.ConfigFactory
import collection.JavaConversions._
import com.packt.masteringakka.bookstore.common.Bootstrap
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.Http

/**
 * Main entry point to startup the application
 */
object Server extends App{
  import akka.http.scaladsl.server.Directives._
  val conf = ConfigFactory.load.getConfig("bookstore").resolve()
  
  //Need cassandra to be running before the singleton manager tries to use it so
  //simplest thing is to just add a pause in here
  println("Waiting 10 seconds to make sure that docker networking is up first...")
  Thread.sleep(10000)

  implicit val system = ActorSystem("BookstoreSystem", conf)
  implicit val mater = ActorMaterializer()
  val log = Logging(system.eventStream, "Server")
  import system.dispatcher

  //Boot up each service module from the config and get the routes
  val routes = 
    conf.
      getStringList("serviceBoots").
      map(toBootClass).
      flatMap(_.bootup(system)).
      map(_.routes)    
  val definedRoutes = routes.reduce(_~_)
  val finalRoutes = 
    pathPrefix("api")(definedRoutes ) ~
    PretendCreditCardService.routes //manually add in the pretend credit card service to the routing tree
  
  val serverSource =
    Http().bind(interface = "0.0.0.0", port = conf.getInt("httpPort"))    
  val sink = Sink.foreach[Http.IncomingConnection](_.handleWith(finalRoutes))
  serverSource.to(sink).run  
  
  def toBootClass(bootPrefix:String) = {
    val clazz = s"com.packt.masteringakka.bookstore.${bootPrefix.toLowerCase}.${bootPrefix}Boot"
    Class.forName(clazz).newInstance.asInstanceOf[Bootstrap]
  }
}

