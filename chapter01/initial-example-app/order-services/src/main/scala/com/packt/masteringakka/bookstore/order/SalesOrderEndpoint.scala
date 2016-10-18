package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common.BookstorePlan
import unfiltered.response.ResponseString
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import unfiltered.response.InternalServerError
import akka.actor.ActorSystem
import akka.actor.ActorRef
import unfiltered.request._
import unfiltered.request.Seg
import io.netty.channel.ChannelHandler.Sharable

/**
 * Http endpoint class for sales order related actions
 */
@Sharable
class SalesOrderEndpoint(salesHandler:ActorRef)(implicit val ec:ExecutionContext) extends BookstorePlan{
  import akka.pattern.ask

  /** Unfilterd Param for the userId input for searching by userId*/
  object UserIdParam extends Params.Extract("userId", Params.first ~> Params.int)
  
  /** Unfilterd Param for the bookId input for searching by bookId*/
  object BookIdParam extends Params.Extract("bookId", Params.first ~> Params.int)
  
  /** Unfilterd Param for the bookTag input for searching by books by tag*/
  object BookTagParam extends Params.Extract("bookTag", Params.first ~> Params.nonempty )  
  
  def intent = {
    case req @ GET(Path(Seg("api" :: "order" :: IntPathElement(id) :: Nil))) =>
      val f = (salesHandler ? FindOrderById(id))
      respond(f, req)
      
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(UserIdParam(userId)) =>
      val f = (salesHandler ? FindOrdersForUser(userId))
      respond(f, req) 
      
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookIdParam(bookId)) =>
      val f = (salesHandler ? FindOrdersForBook(bookId))
      respond(f, req) 
      
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookTagParam(tag)) =>
      val f = (salesHandler ? FindOrdersForBookTag(tag))
      respond(f, req)       
    
    case req @ POST(Path(Seg("api" :: "order" :: Nil))) =>
      val createReq = parseJson[CreateOrder](Body.string(req))
      val f = (salesHandler ? createReq)
      respond(f, req)          
  }
}