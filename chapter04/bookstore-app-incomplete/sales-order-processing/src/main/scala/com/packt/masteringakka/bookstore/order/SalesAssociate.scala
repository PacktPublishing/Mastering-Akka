package com.packt.masteringakka.bookstore.order

import akka.actor._
import slick.jdbc.GetResult
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import concurrent.duration._
import com.packt.masteringakka.bookstore.inventory.InventoryClerk
import com.packt.masteringakka.bookstore.common._
import java.util.UUID
import com.packt.masteringakka.bookstore.credit.CreditCardInfo

/**
 * Companion to the SalesOrderManager service
 */
object SalesAssociate{
  val Name = "sales-associate"
  def props = Props[SalesAssociate]
   
  case class CreateNewOrder(userEmail:String, lineItems:List[SalesOrder.LineItemRequest], cardInfo:CreditCardInfo)
  case class FindOrderById(id:String)
  case class FindOrdersForBook(bookId:Int)
  case class FindOrdersForUser(userId:Int)
  case class FindOrdersForBookTag(tag:String)
}

/**
 * Factory for performing actions related to sales orders
 */
class SalesAssociate extends Aggregate[SalesOrderFO, SalesOrder]{
  import SalesAssociate._
  import InventoryClerk._
  import SalesOrder._
  import Command._
  import PersistentEntity._
  import context.dispatcher
  
  context.system.eventStream.subscribe(self, classOf[InventoryAllocated])
  context.system.eventStream.subscribe(self, classOf[InventoryBackOrdered])
  
  def entityProps(id:String) = SalesOrder.props(id)
 
  def receive = {
    case FindOrderById(id) =>
      val order = lookupOrCreateChild(id)
      order.forward(GetState)      
      
    /*case FindOrdersForUser(userId) =>
      val result = multiEntityLookup(repo.findOrderIdsForUser(userId))
      pipeResponse(result)
      
    case FindOrdersForBook(bookId) =>
      val result = multiEntityLookup(repo.findOrderIdsForBook(bookId))
      pipeResponse(result)
      
    case FindOrdersForBookTag(tag) =>
      val result = multiEntityLookup(repo.findOrderIdsForBookTag(tag))
      pipeResponse(result)  
    */
    
    case req:CreateNewOrder =>
      val newId = UUID.randomUUID().toString
      val entity = lookupOrCreateChild(newId)
      val orderReq = SalesOrder.Command.CreateOrder(newId, req.userEmail, req.lineItems, req.cardInfo )
      entity.forward(orderReq)
      
    case InventoryAllocated(id) =>
      forwardCommand(id, UpdateOrderStatus(SalesOrderStatus.Approved))
      
    case InventoryBackOrdered(id) =>
      forwardCommand(id, UpdateOrderStatus(SalesOrderStatus.BackOrdered))
  }
  
}
