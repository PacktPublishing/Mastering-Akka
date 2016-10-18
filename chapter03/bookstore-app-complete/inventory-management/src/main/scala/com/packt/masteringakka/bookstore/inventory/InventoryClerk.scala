package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.BookstoreActor
import akka.actor.Props
import akka.actor.ActorRef
import com.packt.masteringakka.bookstore.common.ServiceResult
import akka.util.Timeout
import scala.concurrent.Future
import com.packt.masteringakka.bookstore.common.FullResult
import java.util.Date
import com.packt.masteringakka.bookstore.common.EntityAggregate

/**
 * Companion to the InventoryClerk actor where the vocab is defined 
 */
object InventoryClerk{
  //Query operations
  case class FindBook(id:Int)
  case class FindBooksByTags(tags:Seq[String])
  case class FindBooksByAuthor(author:String)
  
  //Command operations
  case class CatalogNewBook(title:String, author:String, tags:List[String], cost:Double)
  case class CategorizeBook(bookId:Int, tag:String)
  case class UncategorizeBook(bookId:Int, tag:String)
  case class IncreaseBookInventory(bookId:Int, amount:Int)
  case class RemoveBookFromCatalog(id:Int)
  
  //Events
  case class OrderCreated(id:Int, books:List[(Int,Int)])
  case class InventoryAllocated(orderId:Int)
  case class InventoryBackOrdered(orderId:Int)
  
  def props = Props[InventoryClerk]
  
  val Name = "inventory-clerk"
}

/**
 * Aggregate actor for managing the book entities 
 */
class InventoryClerk extends EntityAggregate[BookFO, Book]{
  import InventoryClerk._
  import com.packt.masteringakka.bookstore.common.EntityActor._
  import context.dispatcher
  val repo = new BookRepository
  
  //Listen for the OrderCreatd event
  context.system.eventStream.subscribe(self, classOf[OrderCreated])
  
  def receive = {
    case FindBook(id) =>
      log.info("Finding book {}", id)
      val book = lookupOrCreateChild(id)
      book.forward(GetFieldsObject)
      
    case FindBooksByTags(tags) =>
      log.info("Finding books for tags {}", tags)
      val result = multiEntityLookup(repo.findBookIdsByTags(tags))          
      pipeResponse(result)
      
    case FindBooksByAuthor(author) =>
      log.info("Finding books for author {}", author)
      val result = multiEntityLookup(repo.findBookIdsByAuthor(author))
      pipeResponse(result)  
      
    case CatalogNewBook(title, author, tags, cost) =>
      log.info("Cataloging new book with title {}", title)
      val vo = BookFO(0, title, author, tags, cost, 0, new Date, new Date)
      persistOperation(vo.id, vo)
      
    case IncreaseBookInventory(id, amount) =>
      persistOperation(id, Book.AddInventory(amount))
      
    case CategorizeBook(id, tag) =>
      persistOperation(id, Book.AddTag(tag))
      
    case UncategorizeBook(id, tag) =>
      persistOperation(id, Book.RemoveTag(tag))
      
    case RemoveBookFromCatalog(id) =>
      persistOperation(id, Delete)
      
    case OrderCreated(id, lineItems) =>
      import akka.pattern.ask
      import concurrent.duration._
      implicit val timeout = Timeout(5 seconds)
      
      //Allocate inventory from each book
      log.info("Received OrderCreated event for order id {}", id)
      val futs = 
        lineItems.
          map{
            case (bookId, quant) =>
              val f = (lookupOrCreateChild(bookId) ? Book.AllocateInventory(quant)).mapTo[ServiceResult[BookFO]]
              f.filter(_.isValid)
          }
      
      //If we get even one failure, consider it backordered
      Future.sequence(futs).
        map{ _ =>
          log.info("Inventory available for order {}", id)
          InventoryAllocated(id)        
        }.
        recover{
          case ex => 
            log.warning("Inventory back ordered for order {}", id)
            InventoryBackOrdered(id)
        }.
        foreach(context.system.eventStream.publish)     
  }
    
  def entityProps(id:Int) = Book.props(id)
}
