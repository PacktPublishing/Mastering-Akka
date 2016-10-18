package com.packt.masteringakka.bookstore.inventory

import akka.actor.Props
import com.packt.masteringakka.bookstore.common.ServiceResult
import akka.util.Timeout
import scala.concurrent.Future
import java.util.Date
import java.util.UUID
import com.packt.masteringakka.bookstore.common.Aggregate
import akka.pattern.ask
import com.packt.masteringakka.bookstore.common.PersistentEntity.GetState
import com.packt.masteringakka.bookstore.common.PersistentEntity.MarkAsDeleted
import scala.concurrent.duration.DurationInt
import com.packt.masteringakka.bookstore.common.ResumableProjection
import akka.persistence.query.PersistenceQuery
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.stream.ActorMaterializer
import akka.persistence.query.EventEnvelope

/**
 * Companion to the InventoryClerk actor where the vocab is defined 
 */
object InventoryClerk{
  case class FindBook(id:String)
  
  //Command operations
  case class CatalogNewBook(title:String, author:String, tags:List[String], cost:Double, id: Option[String])
  case class CategorizeBook(bookId:String, tag:String)
  case class UncategorizeBook(bookId:String, tag:String)
  case class IncreaseBookInventory(bookId:String, amount:Int)
  case class RemoveBookFromCatalog(id:String)
  
  trait SalesOrderCreateInfo{
    def id:String
    def lineItemInfo:List[(String, Int)]
  }
  
  def props = Props[InventoryClerk]
  
  val Name = "inventory-clerk"
}

/**
 * Aggregate root actor for managing the book entities 
 */
class InventoryClerk extends Aggregate[BookFO, Book]{
  import InventoryClerk._
  import com.packt.masteringakka.bookstore.common.PersistentEntity._
  import context.dispatcher
  import Book.Command._
  
  val projection = ResumableProjection("inventory-allocation", context.system)
  implicit val mater = ActorMaterializer()
  val journal = PersistenceQuery(context.system).
    readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  projection.fetchLatestOffset.foreach{ o =>
    journal.
      eventsByTag("ordercreated", o.getOrElse(0L)).
      runForeach(e => self ! e)
  }
  
  def receive = {
    case FindBook(id) =>
      log.info("Finding book {}", id)
      val book = lookupOrCreateChild(id)
      forwardCommand(id, GetState)
          
    case CatalogNewBook(title, author, tags, cost, optionalId) =>
      log.info("Cataloging new book with title {}", title)
      val id = optionalId.getOrElse(UUID.randomUUID().toString()) // optionally an ID can be posted for testing purposes only
      val fo = BookFO(id, title, author, tags, cost, 0, new Date)
      val command = CreateBook(fo)
      forwardCommand(id, command)
      
    case IncreaseBookInventory(id, amount) =>
      forwardCommand(id, AddInventory(amount))
      
    case CategorizeBook(id, tag) =>
      forwardCommand(id, AddTag(tag))
      
    case UncategorizeBook(id, tag) =>
      forwardCommand(id, RemoveTag(tag))
      
    case RemoveBookFromCatalog(id) =>
      forwardCommand(id, MarkAsDeleted)
      
    case EventEnvelope(offset, pid, seq, order:SalesOrderCreateInfo) =>
      
      //Allocate inventory from each book
      log.info("Received OrderCreated event for order id {}", order.id)
      order.lineItemInfo.
        foreach{
          case (bookId, quant) =>
            forwardCommand(bookId, AllocateInventory(order.id, quant))
        }
      projection.storeLatestOffset(offset)
  }
    
  def entityProps(id:String) = Book.props(id)
}
