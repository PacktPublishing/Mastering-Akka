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
import akka.http.scaladsl.marshalling.ToResponseMarshallable

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

trait MyJsonProtocol extends DefaultJsonProtocol{
  implicit val personFormat = jsonFormat2(Person)
}

object LowLevelHttpServer extends App with MyJsonProtocol{
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.pattern.ask
  implicit val timeout = Timeout(5 seconds)
 
  val personDb = system.actorOf(Props[PersonDb])
  
  val requestHandler:HttpRequest => Future[HttpResponse] = {
    case HttpRequest(GET, Uri.Path("/api/person"), _, _, _) =>
      val peopleFut = (personDb ? PersonDb.FindAllPeople).mapTo[List[Person]]
      peopleFut.map{ people =>
        val respJson = people.toJson
        val ent = HttpEntity(ContentTypes.`application/json`, respJson.prettyPrint )
        HttpResponse(StatusCodes.OK, entity = ent)
      }
      
    case HttpRequest(POST, Uri.Path("/api/person"), _, ent, _) =>
      val strictEntFut = ent.toStrict(timeout.duration)
      for{
        strictEnt <- strictEntFut
        person = strictEnt.data.utf8String.parseJson.convertTo[Person]
        result <- (personDb ? PersonDb.CreatePerson(person)).mapTo[Person]
      } yield {
        val respJson = result.toJson
        val ent = HttpEntity(ContentTypes.`application/json`, respJson.prettyPrint )
        HttpResponse(StatusCodes.OK, entity = ent)
      }
      
    case req:HttpRequest =>
      req.discardEntityBytes()
      Future.successful(HttpResponse(StatusCodes.NotFound ))
  }
  
  val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http().bind(interface = "localhost", port = 8080)
   
  val sink = Sink.foreach[Http.IncomingConnection](_.handleWithAsyncHandler(requestHandler))
  serverSource.to(sink).run
}

case class Person(id:Int, name:String)

object PersonDb{
  case class CreatePerson(person:Person)
  case object FindAllPeople
}

class PersonDb extends Actor{
  import PersonDb._
  var people:Map[Int, Person] = Map.empty
  
  def receive = {
    case FindAllPeople =>
      sender ! people.values.toList
      
    case CreatePerson(person) =>
      people = people ++ Map(person.id -> person)
      sender ! person
  }
}