package com.packt.masteringakka.bookstore.user

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import com.packt.masteringakka.bookstore.common.BookstorePlan
import akka.actor.ActorRef
import unfiltered.request._
import io.netty.channel.ChannelHandler.Sharable
import com.packt.masteringakka.bookstore.domain.user.UserInput
import com.packt.masteringakka.bookstore.domain.user.UpdateUserInfo
import com.packt.masteringakka.bookstore.domain.user.FindUserById
import com.packt.masteringakka.bookstore.domain.user.CreateUser
import com.packt.masteringakka.bookstore.domain.user.FindUserByEmail
import com.packt.masteringakka.bookstore.domain.user.DeleteUser

/**
 * Http endpoint class for performing user related actions
 */
@Sharable
class UserEndpoint(userManager:ActorRef)(implicit val ec:ExecutionContext) extends BookstorePlan{
  import akka.pattern.ask
  
  /** Unfiltered param for email address */
  object EmailParam extends Params.Extract("email", Params.first ~> Params.nonempty)
  
  def intent = {
    case req @ GET(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val f = (userManager ? FindUserById(userId))
      respond(f, req)
      
    case req @ GET(Path(Seg("api" :: "user" :: Nil))) & Params(EmailParam(email)) =>
      val f = (userManager ? FindUserByEmail(email))
      respond(f, req)      
    
    case req @ POST(Path(Seg("api" :: "user" :: Nil))) =>
      val input = parseJson[UserInput](Body.string(req))
      val f = (userManager ? CreateUser(input))
      respond(f, req)
      
    case req @ PUT(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val input = parseJson[UserInput](Body.string(req))
      val f = (userManager ? UpdateUserInfo(userId, input))
      respond(f, req) 
      
    case req @ DELETE(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val f = (userManager ? DeleteUser(userId))
      respond(f, req)       
  }
}