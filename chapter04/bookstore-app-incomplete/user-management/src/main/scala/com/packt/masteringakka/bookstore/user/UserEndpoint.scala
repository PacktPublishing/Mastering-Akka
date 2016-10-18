package com.packt.masteringakka.bookstore.user

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import com.packt.masteringakka.bookstore.common.BookstorePlan
import akka.actor.ActorRef
import unfiltered.request._
import io.netty.channel.ChannelHandler.Sharable

/**
 * Http endpoint class for performing user related actions
 */
@Sharable
class UserEndpoint(crm:ActorRef)(implicit val ec:ExecutionContext) extends BookstorePlan{
  import akka.pattern.ask
  import CustomerRelationsManager._
  
  /** Unfiltered param for email address */
  object EmailParam extends Params.Extract("email", Params.first ~> Params.nonempty)
  
  def intent = {
    case req @ GET(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val f = (crm ? FindUserById(userId))
      respond(f, req)
      
    case req @ GET(Path(Seg("api" :: "user" :: Nil))) & Params(EmailParam(email)) =>
      val f = (crm ? FindUserByEmail(email))
      respond(f, req)      
    
    case req @ POST(Path(Seg("api" :: "user" :: Nil))) =>
      val input = parseJson[BookstoreUser.UserInput](Body.string(req))
      val f = (crm ? SignupNewUser(input))
      respond(f, req)
      
    case req @ PUT(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val input = parseJson[BookstoreUser.UserInput](Body.string(req))
      val f = (crm ? UpdateUserInfo(userId, input))
      respond(f, req) 
      
    case req @ DELETE(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val f = (crm ? RemoveUser(userId))
      respond(f, req)   
  }
}