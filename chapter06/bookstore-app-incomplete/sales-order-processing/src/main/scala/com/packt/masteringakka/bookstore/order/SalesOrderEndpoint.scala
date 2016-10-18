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
import org.json4s.ext.EnumNameSerializer

/**
 * Http endpoint class for sales order related actions
 */
@Sharable
class SalesOrderEndpoint(salesAssociate:ActorRef, salesOrderView:ActorRef)(implicit val ec:ExecutionContext) extends BookstorePlan{
  import akka.pattern.ask
  import SalesAssociate._
  import SalesOrder._
  import SalesOrderView._
  
  override def additionalSerializers = List(new EnumNameSerializer(LineItemStatus))
  
  /** Unfilterd Param for the userId input for searching by userId*/
  object EmailParam extends Params.Extract("email", Params.first ~> Params.nonempty )
  
  /** Unfilterd Param for the bookId input for searching by bookId*/
  object BookIdParam extends Params.Extract("bookId", Params.first ~> Params.nonempty )
  
  /** Unfilterd Param for the bookTag input for searching by books by tag*/
  object BookTagParam extends Params.Extract("bookTag", Params.first ~> Params.nonempty )  
  
  def intent = {
    case req @ GET(Path(Seg("api" :: "order" :: id :: Nil))) =>
      val f = (salesAssociate ? FindOrderById(id))
      respond(f, req)
      
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(EmailParam(email)) =>
      val f = (salesOrderView ? FindOrdersForUser(email))
      respond(f, req) 
      
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookIdParam(bookId)) =>
      val f = (salesOrderView ? FindOrdersForBook(bookId))
      respond(f, req) 
      
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookTagParam(tag)) =>
      val f = (salesOrderView ? FindOrdersForBookTag(tag))
      respond(f, req)       
    
    case req @ POST(Path(Seg("api" :: "order" :: Nil))) =>
      val createReq = parseJson[CreateNewOrder](Body.string(req))
      val f = (salesAssociate ? createReq)
      respond(f, req)          
  }
}