package com.packt.masteringakka.bookstore.user

import akka.actor.ActorRef
import com.packt.masteringakka.bookstore.common.BookstorePlan
import io.netty.channel.ChannelHandler.Sharable
import unfiltered.request._

import scala.concurrent.ExecutionContext

/**
 * Http endpoint class for performing user related actions
 */
@Sharable
class UserEndpoint(crm:ActorRef)(implicit val ec:ExecutionContext) extends BookstorePlan{
  import CustomerRelationsManager._
  import akka.pattern.ask
  
  
  def intent = {
      
    case req @ GET(Path(Seg("api" :: "user" :: email :: Nil))) =>
      val f = (crm ? FindUserByEmail(email))
      respond(f, req)      
    
    case req @ POST(Path(Seg("api" :: "user" :: Nil))) =>
      val input = parseJson[CustomerRelationsManager.CreateUserInput](Body.string(req))
      val f = (crm ? SignupNewUser(input))
      respond(f, req)
      
    case req @ PUT(Path(Seg("api" :: "user" :: email :: Nil))) =>
      val input = parseJson[BookstoreUser.UserInput](Body.string(req))
      val f = (crm ? UpdateUserInfo(email, input))
      respond(f, req) 
      
    case req @ DELETE(Path(Seg("api" :: "user" :: email :: Nil))) =>
      val f = (crm ? RemoveUser(email))
      respond(f, req)   
  }
}