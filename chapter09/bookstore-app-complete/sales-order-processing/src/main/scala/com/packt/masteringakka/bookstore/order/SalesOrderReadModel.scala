package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common._
import java.util.Date
import akka.actor.Props
import akka.stream.scaladsl.Source
import akka.util.Timeout
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Flow
import akka.stream.ActorMaterializer
import com.typesafe.conductr.lib.akka.ImplicitConnectionContext
import scala.concurrent.Future
import com.packt.masteringakka.bookstore.common.ServiceConsumer
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpMethods

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

class SalesOrderViewBuilder extends ViewBuilder[SalesOrderViewBuilder.SalesOrderRM] 
  with SalesOrderReadModel with OrderJsonProtocol with ServiceConsumer with ImplicitConnectionContext{
  
  import SalesOrder._
  import SalesOrder.Event._
  import ViewBuilder._
  import SalesOrderViewBuilder._
  import akka.pattern.ask
  import concurrent.duration._
  implicit val timeout = Timeout(5 seconds)  
  implicit val rmFormats = orderRmFormat     
  
  val bookLookup = 
    Flow[SalesOrderLineItemFO].
      mapAsyncUnordered(4)(item => findBook(item.bookId )).
      fold(Map.empty[String, Book]){
        case (books, Some(b)) => books ++ Map(b.id -> b) 
        case (books, other) => 
          log.error("Encountered a non successful result looking up a book: {}", other)
          books
      } 
  
  def projectionId = "sales-order-view-builder"
    
  def actionFor(id:String, env:EventEnvelope) = env.event match {
    case OrderCreated(order) =>
      //Load all of the books that we need to denormalize the data
      //order.lineItems.foreach(item => invClerk ! InventoryClerk.FindBook(item.bookId))
      //context.become(loadingData(order, offset, Map.empty, order.lineItems.size))      
      val flow = 
        Flow[EnvelopeAndAction].
          mapConcat{ _ => order.lineItems}.
          via(bookLookup).
          map{ books => 
            val lineItems = order.lineItems.flatMap{ item =>
              books.get(item.bookId).map{b => 
                val itemBook = LineItemBook(b.id, b.title, b.author, b.tags)
                (item.lineItemNumber.toString, SalesOrderLineItem(item.lineItemNumber, itemBook, item.quantity, item.cost, item.status.toString))
              }
            }
            val salesOrderRm = SalesOrderRM(order.id, order.userId, order.creditTxnId, 
              order.totalCost, lineItems.toMap, order.createTs, order.deleted )
            EnvelopeAndAction(env, InsertAction(id, salesOrderRm))
          }   
      DeferredCreate(flow)                       
      
    case LineItemStatusUpdated(bookId, itemNumber, status) =>
      UpdateAction(id, s"lineItems['${itemNumber}'].status = newStatus", Map("newStatus" -> status.toString()))
  }
  
  def findBook(id:String):Future[Option[Book]] = {
    import context.dispatcher
    val fut = 
      for{
        result <- lookupService("inventory-management")
        if result.uriOpt.isDefined      
        requestUri = Uri(result.uriOpt.get.toString).withPath(Uri.Path("/api") / "book" / id)
        book <- executeHttpRequest[Book](HttpRequest(HttpMethods.GET, requestUri))
      } yield Some(book)
    fut.recover{
      case ex:Throwable => 
        log.error(ex, "Error loading book {}", id)
        None
    }
  }
}

object SalesOrderView{
  val Name = "sales-order-view"
  case class FindOrdersForBook(bookId:String)
  case class FindOrdersForUser(email:String)
  case class FindOrdersForBookTag(tag:String)   
  def props = Props[SalesOrderView]
}

class SalesOrderView extends SalesOrderReadModel with BookstoreActor with ElasticsearchSupport with OrderJsonProtocol{
  import SalesOrderView._
  import SalesOrderViewBuilder._
  import ElasticsearchApi._
  import context.dispatcher
  implicit val mater = ActorMaterializer()
  
  def receive = {
    case FindOrdersForBook(bookId) =>
      val results = queryElasticsearch[SalesOrderRM](s"lineItems.\\*.book.id:$bookId")
      pipeResponse(results)
      
    case FindOrdersForUser(email) =>
      val results = queryElasticsearch[SalesOrderRM](s"userEmail:$email")
      pipeResponse(results)
      
    case FindOrdersForBookTag(tag) =>
      println(tag)
      val results = queryElasticsearch[SalesOrderRM](s"lineItems.\\*.book.tags:$tag")
      pipeResponse(results)      
    
  }
}