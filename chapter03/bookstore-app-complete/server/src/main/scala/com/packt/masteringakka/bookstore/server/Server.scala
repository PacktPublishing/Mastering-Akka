package com.packt.masteringakka.bookstore.server
import akka.actor._
import com.typesafe.config.ConfigFactory
import collection.JavaConversions._
import com.packt.masteringakka.bookstore.common.PostgresDb
import com.packt.masteringakka.bookstore.common.Bootstrap
import akka.event.Logging

/**
 * Main entry point to startup the application
 */
object Server extends App{
  val conf = ConfigFactory.load.getConfig("bookstore")
  PostgresDb.init(conf) 
  implicit val system = ActorSystem("Bookstore", conf)
  val log = Logging(system.eventStream, "Server")
  import system.dispatcher

  //Boot up each service module from the config and get the endpoints from it
  val endpoints = 
    conf.
      getStringList("serviceBoots").
      map(toBootClass).
      flatMap(_.bootup(system))
    
  val server = endpoints.foldRight(unfiltered.netty.Server.http(8080)){
    case (endpoint, serv) => 
      log.info("Adding endpoint: {}", endpoint)
      serv.plan(endpoint)
  }
  
  //Adding in the pretend credit card charging service too so that the app works
  server.plan(PretentCreditCardService).run()
  
  def toBootClass(bootPrefix:String) = {
    val clazz = s"com.packt.masteringakka.bookstore.${bootPrefix.toLowerCase}.${bootPrefix}Boot"
    Class.forName(clazz).newInstance.asInstanceOf[Bootstrap]
  }
}

