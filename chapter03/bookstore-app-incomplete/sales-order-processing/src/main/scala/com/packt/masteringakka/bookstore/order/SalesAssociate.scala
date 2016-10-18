package com.packt.masteringakka.bookstore.order

import akka.actor._
import slick.jdbc.GetResult
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import concurrent.duration._
import com.packt.masteringakka.bookstore.inventory.InventoryClerk
import com.packt.masteringakka.bookstore.common._

/**
 * Companion to the SalesOrderManager service
 */
object SalesAssociate{
  val Name = "sales-associate"
  def props = Props[SalesAssociate]
   
  case class FindOrderById(id:Int)
  case class FindOrdersForBook(bookId:Int)
  case class FindOrdersForUser(userId:Int)
  case class FindOrdersForBookTag(tag:String)
}

/**
 * Factory for performing actions related to sales orders
 */
class SalesAssociate extends EntityAggregate[SalesOrderFO, SalesOrder]{
  import SalesAssociate._
  import InventoryClerk._
  import SalesOrder._
  import EntityActor._
  import context.dispatcher
  val repo = new SalesOrderRepository
  context.system.eventStream.subscribe(self, classOf[InventoryAllocated])
  context.system.eventStream.subscribe(self, classOf[InventoryBackOrdered])
  
  def entityProps(id:Int) = SalesOrder.props(id)
 
  def receive = {
    case FindOrderById(id) =>
      val order = lookupOrCreateChild(id)
      order.forward(GetFieldsObject)      
      
    case FindOrdersForUser(userId) =>
      val result = multiEntityLookup(repo.findOrderIdsForUser(userId))
      pipeResponse(result)
      
    case FindOrdersForBook(bookId) =>
      val result = multiEntityLookup(repo.findOrderIdsForBook(bookId))
      pipeResponse(result)
      
    case FindOrdersForBookTag(tag) =>
      val result = multiEntityLookup(repo.findOrderIdsForBookTag(tag))
      pipeResponse(result)    
    
    case req:CreateOrder =>
      val agg = lookupOrCreateChild(0)
      agg.forward(req)
      
    case InventoryAllocated(id) =>
      persistOperation(id, 
        UpdateOrderStatus(SalesOrderStatus.Approved))
      
    case InventoryBackOrdered(id) =>
      persistOperation(id, 
        UpdateOrderStatus(SalesOrderStatus.BackOrdered ))
  }
  
}
