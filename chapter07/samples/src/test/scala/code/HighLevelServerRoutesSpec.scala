package code

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import akka.testkit.TestProbe
import akka.http.scaladsl.model.StatusCodes
import spray.json._
import akka.http.scaladsl.server._
import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes

class HighLevelServerRoutesSpec 
  extends WordSpec with Matchers with ScalatestRouteTest with HighLevelHttpRoutes{
  val person1 = Person(123, "Chris Baxter")
  val person2 = Person(456, "Bill Williams")
  val people = List(person1, person2)
  
  def withProbe(f:TestProbe => Unit):Unit = {
    val probe = TestProbe()
    f(probe)
  }  
  
  "A GET request to the /api/person path" should{
    "make a FindAllPeople request to the personDb and respond with the results" in withProbe{ actorProbe =>
      val result = Get("/api/person") ~> routes(actorProbe.ref) ~> runRoute
      actorProbe.expectMsg(PersonDb.FindAllPeople)
      actorProbe.reply(people)
      check{
        status shouldEqual StatusCodes.OK 
        responseAs[String] shouldEqual people.toJson.prettyPrint
      }(result)
    }
  }
  
  "A POST request to the /api/person path" should{
    "reject the request when there is no json body" in withProbe{ actorProbe =>
      Post("/api/person") ~> routes(actorProbe.ref) ~> check{
        handled shouldEqual false
        rejections should not be empty  
        val rej = rejections.
          collect{case r:MalformedRequestContentRejection => r} 
        rej should not be empty
      }
    }
    "pass on a request to create a person when there is a json body" in withProbe{ actorProbe =>
      val ent = HttpEntity(ContentTypes.`application/json`, person1.toJson.prettyPrint)
      val result = Post("/api/person", entity = ent) ~> routes(actorProbe.ref) ~> runRoute
      actorProbe.expectMsg(PersonDb.CreatePerson(person1))
      actorProbe.reply(person1)
      check{
        handled shouldEqual true
      }(result)
    }    
  }
}