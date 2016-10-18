package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.BookstorePlan
import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import unfiltered.request._
import unfiltered.request.Seg
import io.netty.channel.ChannelHandler.Sharable
import unfiltered.response.Pass
import akka.pattern.ask

/**
 * Http Endpoint for requests related to inventory management 
 */
@Sharable
class InventoryEndpoint(inventoryClerk:ActorRef)(implicit val ec:ExecutionContext) extends BookstorePlan{
  import akka.pattern.ask
  import InventoryClerk._
  
  /**
   * Unfiltered param for handling the multi value tag param
   */
  object TagParam extends Params.Extract("tag", {values => 
    val filtered = values.filter(_.nonEmpty)
    if (filtered.isEmpty) None else Some(filtered) 
  })
  
  /** Unfiltered param for the author param*/
  object AuthorParam extends Params.Extract("author", Params.first ~> Params.nonempty)

  def intent = {
    case req @ GET(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: Nil))) =>
      val f = (inventoryClerk ? FindBook(bookId))
      respond(f, req)
      
    case req @ GET(Path(Seg("api" :: "book" :: Nil))) & Params(TagParam(tags)) =>
      val f = (inventoryClerk ? FindBooksByTags(tags))
      respond(f, req) 
      
    case req @ GET(Path(Seg("api" :: "book" :: Nil))) & Params(AuthorParam(author)) =>
      val f = (inventoryClerk ? FindBooksByAuthor(author))
      respond(f, req)       
      
    case req @ POST(Path(Seg("api" :: "book" :: Nil))) =>
      val createBook = parseJson[CatalogNewBook](Body.string(req))
      val f = (inventoryClerk ? createBook)
      respond(f, req)
      
    case req @ Path(Seg("api" :: "book" :: IntPathElement(bookId) :: "tag" :: tag :: Nil)) =>
      req match{
        case PUT(_) => 
          respond((inventoryClerk ? CategorizeBook(bookId, tag)), req)
        case DELETE(_) => 
          respond((inventoryClerk ? UncategorizeBook(bookId, tag)), req)
        case other => 
          req.respond(Pass)
      }
      
    case req @ PUT(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: "inventory" :: IntPathElement(amount) :: Nil))) =>
      val f = (inventoryClerk ? IncreaseBookInventory(bookId, amount))
      respond(f, req)
      
    case req @ DELETE(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: Nil))) =>
      val f = (inventoryClerk ? RemoveBookFromCatalog(bookId))
      respond(f, req)  
    
  }
}