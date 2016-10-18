package com.packt.masteringakka.bookstore.user

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import com.packt.masteringakka.bookstore.common.BookstoreRoutesDefinition
import com.packt.masteringakka.bookstore.common.BookstoreJsonProtocol
import akka.stream.Materializer
import akka.http.scaladsl.server.Route
import BookstoreUserViewBuilder._
import BookstoreUser._
import CustomerRelationsManager._

/**
 * Http routes class for performing user related actions
 */
class UserRoutes(crm:ActorRef, view:ActorRef)(implicit val ec:ExecutionContext) extends BookstoreRoutesDefinition with UserJsonProtocol{
  import BookstoreUserView._
  import akka.http.scaladsl.server.Directives._
  
  
  def routes(implicit system:ActorSystem, ec:ExecutionContext, mater:Materializer):Route = {
    pathPrefix("user"){
      path(Segment){ email =>
        get{
          serviceAndComplete[BookstoreUserFO](FindUserByEmail(email), crm)
        } ~
        put{
          entity(as[UserInput]){ input =>
            serviceAndComplete[BookstoreUserFO](UpdateUserInfo(email, input), crm)
          }  
        } ~
        delete{
          serviceAndComplete[BookstoreUserFO](RemoveUser(email), crm)
        }
      } ~
      pathEndOrSingleSlash{
        (get & parameter('name)){ name =>
          serviceAndComplete[List[BookstoreUserRM]](FindUsersByName(name), view)
        } ~
        post{
          entity(as[CreateUserInput]){ input =>
            serviceAndComplete[BookstoreUserFO](SignupNewUser(input), crm)
          }
        }
      }      
    } 
  }
}