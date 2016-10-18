package com.packt.masteringakka.bookstore.order

import akka.actor._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import concurrent.duration._
import com.packt.masteringakka.bookstore.common._
import java.util.UUID
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializer
import akka.persistence.query.PersistenceQuery
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Sink
import java.util.Date

/**
 * Companion to the SalesOrderManager service
 */
object SalesAssociate{
  import SalesOrder._
  
  val Name = "sales-associate"
  def props = Props[SalesAssociate]
     
  
  case class CreateNewOrder(userEmail:String, lineItems:List[SalesOrder.LineItemRequest], cardInfo:CreditCardInfo)
  case class FindOrderById(id:String)
  case class StatusChange(orderId:String, status:LineItemStatus.Value, bookId:String, offset:Long)
}

/**
 * Factory for performing actions related to sales orders
 */
class SalesAssociate extends Aggregate[SalesOrderFO, SalesOrder]{
  import SalesAssociate._
  import SalesOrder._
  import Command._
  import PersistentEntity._
  import context.dispatcher
  
  def entityProps = SalesOrder.props
 
  def receive = {
    case FindOrderById(id) =>
      forwardCommand(id, GetState(id))         
    
    case req:CreateNewOrder =>
      val newId = UUID.randomUUID().toString
      val orderReq = SalesOrder.Command.CreateOrder(newId, req.userEmail, req.lineItems, req.cardInfo )
      forwardCommand(newId, orderReq)
      
    case StatusChange(orderId, status, bookId, offset) =>          
      forwardCommand(orderId, UpdateLineItemStatus(bookId, status, orderId))
  }
  
}

object OrderStatusEventListener{
  val Name = "order-status-event-listener"
  def props(associate:ActorRef) = Props(classOf[OrderStatusEventListener], associate)
}

class OrderStatusEventListener(associate:ActorRef) extends BookstoreActor{
  import SalesAssociate._
  import com.packt.masteringakka.bookstore.inventory.Book.Event._
  import context.dispatcher
  
  val projection = ResumableProjection("order-status", context.system)
  implicit val mater = ActorMaterializer()
  val journal = PersistenceQuery(context.system).
    readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  projection.fetchLatestOffset.foreach{ o =>
    log.info("Order status projection using an offset of: {}", new java.util.Date(o.getOrElse(0L)))
    val allocatedSource = journal.eventsByTag("inventoryallocated", o.getOrElse(0L))
    val backorderedSource = journal.eventsByTag("inventorybackordered", o.getOrElse(0L))
    
    allocatedSource.
      merge(backorderedSource).
      collect{
        case EventEnvelope(offset, pid, seq, event:InventoryAllocated) =>
          StatusChange(event.orderId,  LineItemStatus.Approved, event.bookId, offset)
          
        case EventEnvelope(offset, pid, seq, event:InventoryBackordered) =>
          StatusChange(event.orderId,  LineItemStatus.BackOrdered, event.bookId, offset)
      }.
      runForeach(self ! _)
  }  
  
  def receive = {
    case change @ StatusChange(orderId, status, bookId, offset) =>          
      associate ! change
      projection.storeLatestOffset(offset)
  }
}
