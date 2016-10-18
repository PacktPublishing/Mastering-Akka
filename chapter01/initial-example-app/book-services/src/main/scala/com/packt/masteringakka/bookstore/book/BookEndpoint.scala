package com.packt.masteringakka.bookstore.book

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
import unfiltered.response.Pass
import com.packt.masteringakka.bookstore.domain.book.RemoveTagFromBook
import com.packt.masteringakka.bookstore.domain.book.FindBooksByTags
import com.packt.masteringakka.bookstore.domain.book.FindBooksByAuthor
import com.packt.masteringakka.bookstore.domain.book.FindBook
import com.packt.masteringakka.bookstore.domain.book.CreateBook
import com.packt.masteringakka.bookstore.domain.book.AddTagToBook
import com.packt.masteringakka.bookstore.domain.book.AddInventoryToBook
import com.packt.masteringakka.bookstore.domain.book.DeleteBook

/**
 * Http Endpoint for requests related to book management 
 */
@Sharable
class BookEndpoint(bookManager:ActorRef)(implicit val ec:ExecutionContext) extends BookstorePlan{
  import akka.pattern.ask
  
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
      val f = (bookManager ? FindBook(bookId))
      respond(f, req)
      
    case req @ GET(Path(Seg("api" :: "book" :: Nil))) & Params(TagParam(tags)) =>
      val f = (bookManager ? FindBooksByTags(tags))
      respond(f, req) 
      
    case req @ GET(Path(Seg("api" :: "book" :: Nil))) & Params(AuthorParam(author)) =>
      val f = (bookManager ? FindBooksByAuthor(author))
      respond(f, req)       
      
    case req @ POST(Path(Seg("api" :: "book" :: Nil))) =>
      val createBook = parseJson[CreateBook](Body.string(req))
      val f = (bookManager ? createBook)
      respond(f, req)
      
    case req @ Path(Seg("api" :: "book" :: IntPathElement(bookId) :: "tag" :: tag :: Nil)) =>
      req match{
        case PUT(_) => 
          respond((bookManager ? AddTagToBook(bookId, tag)), req)
        case DELETE(_) => 
          respond((bookManager ? RemoveTagFromBook(bookId, tag)), req)
        case other => 
          req.respond(Pass)
      }
      
    case req @ PUT(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: "inventory" :: IntPathElement(amount) :: Nil))) =>
      val f = (bookManager ? AddInventoryToBook(bookId, amount))
      respond(f, req)
      
    case req @ DELETE(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: Nil))) =>
      val f = (bookManager ? DeleteBook(bookId))
      respond(f, req)      
  }
}