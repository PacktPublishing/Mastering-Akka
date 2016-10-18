package code

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import scala.concurrent.Future
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.actor.Actor
import HttpMethods._
import akka.actor.Props
import concurrent.duration._
import akka.util.Timeout
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import akka.http.scaladsl.server.Route
import akka.actor.ActorRef


trait HighLevelHttpRoutes extends MyJsonProtocol with SprayJsonSupport{
  import akka.pattern.ask
  implicit val timeout = Timeout(5 seconds)
	 
  	  	 
  import akka.http.scaladsl.server.Directives._
	  
  def routes(personDb:ActorRef):Route = 
	path("api" / "person"){
	  get{
	    val peopleFut = (personDb ? PersonDb.FindAllPeople).mapTo[List[Person]]
	    complete(peopleFut)
	  } ~
	  (post & entity(as[Person])){ person =>
	    val fut = (personDb ? PersonDb.CreatePerson(person)).mapTo[Person]
	    complete(fut)
	  }
     }  
   
  
  def run:Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    import system.dispatcher
    implicit val timeout = Timeout(5 seconds)
    val personDb = system.actorOf(Props[PersonDb])
	   
    val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
      Http().bind(interface = "localhost", port = 8080)    
    val sink = Sink.foreach[Http.IncomingConnection](_.handleWith(routes(personDb)))
    serverSource.to(sink).run
  }
}

object HighLevelHttpServer extends App with HighLevelHttpRoutes{  
  run
}