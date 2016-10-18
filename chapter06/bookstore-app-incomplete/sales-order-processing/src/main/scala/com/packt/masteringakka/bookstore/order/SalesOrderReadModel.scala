package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common._
import java.util.Date
import com.packt.masteringakka.bookstore.inventory.BookFO
import com.packt.masteringakka.bookstore.inventory.InventoryClerk
import akka.actor.Props
import akka.stream.scaladsl.Source
import akka.util.Timeout
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Flow

trait SalesOrderReadModel{
  def indexRoot = "order"
  def entityType = SalesOrder.EntityType 
}

object SalesOrderViewBuilder{
  val Name = "sales-order-view-builder"
  case class LineItemBook(id:String, title:String, author:String, tags:List[String])
  case class SalesOrderLineItem(lineItemNumber:Int, book:LineItemBook, quantity:Int, cost:Double, status:String)
  case class SalesOrderRM(id:String, userEmail:String, creditTxnId:String, 
    totalCost:Double, lineItems:Map[String, SalesOrderLineItem], createTs:Date, deleted:Boolean = false) extends ReadModelObject
  def props = Props[SalesOrderViewBuilder]  
}

class SalesOrderViewBuilder extends SalesOrderReadModel with ViewBuilder[SalesOrderViewBuilder.SalesOrderRM]{
  import SalesOrder.Event._
  import ViewBuilder._
  import SalesOrderViewBuilder._
  import akka.pattern.ask
  import concurrent.duration._
  implicit val timeout = Timeout(5 seconds)  
    
  val invClerk = context.actorSelection(s"/user/${InventoryClerk.Name}")
  
  def projectionId = "sales-order-view-builder"
    
  def actionFor(id:String, env:EventEnvelope) = env.event match {
    case OrderCreated(order) =>
      //Load all of the books that we need to denormalize the data
      //order.lineItems.foreach(item => invClerk ! InventoryClerk.FindBook(item.bookId))
      //context.become(loadingData(order, offset, Map.empty, order.lineItems.size))      
      val flow = 
        Flow[EnvelopeAndAction]
      
      DeferredCreate(flow)                       
      
    case LineItemStatusUpdated(bookId, itemNumber, status) =>
      UpdateAction(id, s"lineItems['${itemNumber}'].status = newStatus", Map("newStatus" -> status.toString()))
  } 
}

object SalesOrderView{
  val Name = "sales-order-view"
  case class FindOrdersForBook(bookId:String)
  case class FindOrdersForUser(email:String)
  case class FindOrdersForBookTag(tag:String)   
  def props = Props[SalesOrderView]
}

class SalesOrderView extends SalesOrderReadModel with BookstoreActor with ElasticsearchSupport{
  import SalesOrderView._
  import ElasticsearchApi._
  import context.dispatcher
  
  def receive = {
    case FindOrdersForBook(bookId) =>
      val results = queryElasticsearch(s"lineItems.\\*.book.id:$bookId")
      pipeResponse(results)
      
    case FindOrdersForUser(email) =>
      val results = queryElasticsearch(s"userEmail:$email")
      pipeResponse(results)
      
    case FindOrdersForBookTag(tag) =>
      val results = queryElasticsearch(s"lineItems.\\*.book.tags:$tag")
      pipeResponse(results)      
    
  }
}