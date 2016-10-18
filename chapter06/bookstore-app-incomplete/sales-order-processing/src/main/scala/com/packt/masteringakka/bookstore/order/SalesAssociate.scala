package com.packt.masteringakka.bookstore.order

import akka.actor._
import slick.jdbc.GetResult
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import concurrent.duration._
import com.packt.masteringakka.bookstore.inventory.Book
import com.packt.masteringakka.bookstore.common._
import java.util.UUID
import com.packt.masteringakka.bookstore.credit.CreditCardInfo
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializer
import akka.persistence.query.PersistenceQuery
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.EventEnvelope

/**
 * Companion to the SalesOrderManager service
 */
object SalesAssociate{
  val Name = "sales-associate"
  def props = Props[SalesAssociate]
   
  case class CreateNewOrder(userEmail:String, lineItems:List[SalesOrder.LineItemRequest], cardInfo:CreditCardInfo)
  case class FindOrderById(id:String)
}

/**
 * Factory for performing actions related to sales orders
 */
class SalesAssociate extends Aggregate[SalesOrderFO, SalesOrder]{
  import SalesAssociate._
  import Book.Event._
  import SalesOrder._
  import Command._
  import PersistentEntity._
  import context.dispatcher
  
  val projection = ResumableProjection("order-status", context.system)
  implicit val mater = ActorMaterializer()
  val journal = PersistenceQuery(context.system).
    readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  projection.fetchLatestOffset.foreach{ o =>
    log.info("Order status projection using an offset of: {}", new java.util.Date(o.getOrElse(0L)))
    journal.
      eventsByTag("book", o.getOrElse(0L)).
      runForeach(e => self ! e)
  }  
  
  
  def entityProps(id:String) = SalesOrder.props(id)
 
  def receive = {
    case FindOrderById(id) =>
      val order = lookupOrCreateChild(id)
      order.forward(GetState)           
    
    case req:CreateNewOrder =>
      val newId = UUID.randomUUID().toString
      val entity = lookupOrCreateChild(newId)
      val orderReq = SalesOrder.Command.CreateOrder(newId, req.userEmail, req.lineItems, req.cardInfo )
      entity.forward(orderReq)
      
    case EventEnvelope(offset, pid, seq, event) =>    
      event match{
        case InventoryAllocated(orderId, bookId, amount) =>
          forwardCommand(orderId, UpdateLineItemStatus(bookId, LineItemStatus.Approved))
          
      
        case InventoryBackordered(orderId, bookId) =>
         forwardCommand(orderId, UpdateLineItemStatus(bookId, LineItemStatus.BackOrdered ))
         
        case other =>
          //ignore
      } 
     projection.storeLatestOffset(offset)
  }
  
}
