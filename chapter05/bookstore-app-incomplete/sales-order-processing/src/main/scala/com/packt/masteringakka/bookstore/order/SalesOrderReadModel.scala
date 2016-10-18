package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common._
import java.util.Date
import com.packt.masteringakka.bookstore.inventory.BookFO
import com.packt.masteringakka.bookstore.inventory.InventoryClerk
import akka.actor.Props

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
    
  val invClerk = context.actorSelection(s"/user/${InventoryClerk.Name}")
  
  def projectionId = "sales-order-view-builder"
  
  def actionFor(id:String, offset:Long, event:Any) = event match {
    case OrderCreated(order) =>
      //Load all of the books that we need to denormalize the data
      order.lineItems.foreach(item => invClerk ! InventoryClerk.FindBook(item.bookId))
      context.become(loadingData(order, offset, Map.empty, order.lineItems.size))
      DeferredCreate
      
    case LineItemStatusUpdated(bookId, itemNumber, status) =>
      UpdateAction(id, s"lineItems['${itemNumber}'].status = newStatus", Map("newStatus" -> status.toString()))
  } 
  
  def loadingData(order:SalesOrderFO, offset:Long, books:Map[String,BookFO], needed:Int):Receive = {
    case sr:ServiceResult[_] =>
      
      val newNeeded = needed - 1
      val newBooks = sr match{
        case FullResult(b:BookFO) => books ++ Map(b.id -> b)
        case other =>
          log.error("Unexpected result waiting for book lookup, {}", other)
          books
      }
      
      if (newNeeded <= 0){
        //We have everything we need, build out the final read model object and save
        val lineItems = order.lineItems.flatMap{ item =>
          newBooks.get(item.bookId).map{b => 
            val itemBook = LineItemBook(b.id, b.title, b.author, b.tags)
            (item.lineItemNumber.toString, SalesOrderLineItem(item.lineItemNumber, itemBook, item.quantity, item.cost, item.status.toString))
          }
        }
        val salesOrderRm = SalesOrderRM(order.id, order.userId, order.creditTxnId, 
          order.totalCost, lineItems.toMap, order.createTs, order.deleted )
          
        import context.dispatcher
        updateIndex(order.id, salesOrderRm, None)(context.dispatcher).andThen{
          case tr => resumableProjection.storeLatestOffset(offset)
        }
      }
      else{
        context.become(loadingData(order, offset, newBooks, newNeeded))
      }
      
      
    case _ =>
      stash
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