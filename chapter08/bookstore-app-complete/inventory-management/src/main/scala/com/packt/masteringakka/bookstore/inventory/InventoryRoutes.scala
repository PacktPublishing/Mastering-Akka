package com.packt.masteringakka.bookstore.inventory

import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import akka.pattern.ask
import com.packt.masteringakka.bookstore.common.BookstoreRoutesDefinition
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import com.packt.masteringakka.bookstore.common.BookstoreJsonProtocol
import com.packt.masteringakka.bookstore.inventory.BookViewBuilder._
import com.packt.masteringakka.bookstore.inventory.InventoryClerk._


/**
 * Routes for requests related to inventory management 
 */
class InventoryRoutes(inventoryClerk:ActorRef, bookView:ActorRef)(implicit val ec:ExecutionContext) extends BookstoreRoutesDefinition with InventoryJsonProtocol{
  import BookView._
  import akka.http.scaladsl.server.Directives._
  
  def routes(implicit system:ActorSystem, ec:ExecutionContext, mater:Materializer):Route = {
    pathPrefix("book"){
      get{
        path(Segment){ bookId =>
          serviceAndComplete[BookFO](FindBook(bookId), inventoryClerk)
        } ~
        pathEndOrSingleSlash{
          parameter('author){ author =>
            serviceAndComplete[List[BookRM]](FindBooksByAuthor(author), bookView)
          } ~          
          parameter('tag.*){ tags =>
            serviceAndComplete[List[BookRM]](FindBooksByTags(tags.toList), bookView)
          }         
        }
      } ~
      (post & pathEndOrSingleSlash){
        entity(as[CatalogNewBook]){ msg =>
          serviceAndComplete[BookFO](msg, inventoryClerk)
        }
      } ~
      path(Segment / "tag" / Segment){ (bookId, tag) =>
        put{
          serviceAndComplete[BookFO](CategorizeBook(bookId, tag), inventoryClerk)
        } ~
        delete{
          serviceAndComplete[BookFO](UncategorizeBook(bookId, tag), inventoryClerk)
        }
      } ~
      pathPrefix(Segment){ bookId =>
        (pathEndOrSingleSlash & delete){
          serviceAndComplete[BookFO](RemoveBookFromCatalog(bookId), inventoryClerk)
        } ~
        (path("inventory" / IntNumber) & put){ amount =>
          serviceAndComplete[BookFO](IncreaseBookInventory(bookId, amount), inventoryClerk)
        }
      }
    }
  }
}