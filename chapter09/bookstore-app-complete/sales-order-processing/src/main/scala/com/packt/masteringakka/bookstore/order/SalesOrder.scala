package com.packt.masteringakka.bookstore.order

import java.util.Date
import akka.actor.ActorRef
import akka.actor.Identify
import concurrent.duration._
import akka.actor.ActorIdentity
import akka.actor.FSM
import com.packt.masteringakka.bookstore.common._
import akka.actor.Props
import com.packt.masteringakka.bookstore.common.EntityFieldsObject
import com.packt.masteringakka.bookstore.common.EntityCommand
import com.typesafe.conductr.bundlelib.akka.LocationService
import com.typesafe.conductr.lib.akka.ImplicitConnectionContext
import com.typesafe.conductr.bundlelib.scala._
import java.net.{URI => JavaURI}
import scala.concurrent.Future
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import spray.json.JsonFormat
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.reflect.ClassTag
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.RootJsonFormat

object LineItemStatus extends Enumeration{
  val Unknown, Approved, BackOrdered = Value
}
case class SalesOrderLineItemFO(lineItemNumber:Int, bookId:String, quantity:Int, cost:Double, status:LineItemStatus.Value)


object SalesOrderFO{
  def empty = SalesOrderFO("", "", "", 0, Nil, new Date(0))
}
case class SalesOrderFO(id:String, userId:String, creditTxnId:String, 
    totalCost:Double, lineItems:List[SalesOrderLineItemFO], createTs:Date, deleted:Boolean = false) extends EntityFieldsObject[String, SalesOrderFO]{
    def assignId(id:String) = this.copy(id = id)
    def markDeleted = this.copy(deleted = true)
}

/**
 * Aggregate root for the SalesOrder and SalesOrderLineItem entities
 */
class SalesOrder extends PersistentEntity[SalesOrderFO]{
  import SalesOrder._
  import Command._
  import Event._
  import context.dispatcher
  
  def initialState = SalesOrderFO.empty
  
  def additionalCommandHandling:Receive = {
    case co:CreateOrder =>
      //Kick off the validation process
      val validator = context.actorOf(SalesOrderCreateValidator.props)
      validator.forward(co)
      
    case CreateValidatedOrder(order) =>
      //Now we can persist the complete order
      persist(OrderCreated(order))(handleEventAndRespond())
      
    case UpdateLineItemStatus(bookId, status, id) =>
      val itemNumber = state.lineItems.
        find(_.bookId == bookId).
        map(_.lineItemNumber).
        getOrElse(0)
      persist(LineItemStatusUpdated(bookId, itemNumber, status))(handleEvent)
  }
  
  def handleEvent(event:EntityEvent) = event match {
    case OrderCreated(order) =>
      state = order
      
    case LineItemStatusUpdated(bookId, itemNumber, status) =>
      val newItems = state.lineItems.map{
        case item if item.bookId == bookId => item.copy(status = status)
        case item => item
      }
      state = state.copy(lineItems = newItems)
  }
  
  def isCreateMessage(cmd:Any) = cmd match{
    case co:CreateOrder => true
    case cvo:CreateValidatedOrder => true
    case _ => false
  }  
}

/**
 * Companion to the SalesOrder aggregate 
 */
object SalesOrder{
  import collection.JavaConversions._
  
  val EntityType = "salesorder"
  def props = Props[SalesOrder]
  
  case class CreditCardInfo(cardHolder:String, cardType:String, cardNumber:String, expiration:Date)
  case class LineItemRequest(bookId:String, quantity:Int)
  //Minimally mapped info from the other modules
  case class BookstoreUser(email:String)
  case class Book(id:String, title:String, author:String, tags:List[String], inventoryAmount:Int, cost:Double)
  case class ChargeCreditCard(cardInfo:CreditCardInfo, amount:Double)
  case class CreditTxn(id:String, status:String)    
  
  
  object Command{
    case class CreateOrder(newOrderId:String, userEmail:String, lineItems:List[LineItemRequest], cardInfo:CreditCardInfo) extends EntityCommand{
      def entityId = newOrderId 
    }
    case class CreateValidatedOrder(order:SalesOrderFO) extends EntityCommand{
      def entityId = order.id 
    }
    case class UpdateLineItemStatus(bookId:String, status:LineItemStatus.Value, orderId:String) extends EntityCommand{
      def entityId = orderId
    }    
  }

  object Event{
    trait SalesOrderEvent extends EntityEvent{def entityType = EntityType }
    case class OrderCreated(order:SalesOrderFO) extends SalesOrderEvent {
      
      def toDatamodel = {
        val lineItemsDm = order.lineItems.map{ item =>
          Datamodel.SalesOrderLineItem.newBuilder().
            setBookId(item.bookId).
            setCost(item.cost ).
            setQuantity(item.quantity).
            setLineItemNumber(item.lineItemNumber).
            setStatus(item.status.toString)
            .build
        }
        val orderDm = Datamodel.SalesOrder.newBuilder().
          setId(order.id).
          setCreateTs(order.createTs.getTime).
          addAllLineItem(lineItemsDm).
          setUserId(order.userId ).
          setCreditTxnId(order.creditTxnId).
          setTotalCost(order.totalCost).
          build
        
        Datamodel.OrderCreated.newBuilder().
          setOrder(orderDm).
          build
      }
    }
    object OrderCreated extends DatamodelReader{
      def fromDatamodel = {
        case doc:Datamodel.OrderCreated =>
          val dmo = doc.getOrder()
          val items = dmo.getLineItemList().map{ item =>
            SalesOrderLineItemFO(item.getLineItemNumber(), item.getBookId(), 
              item.getQuantity(), item.getCost(), LineItemStatus.withName(item.getStatus))
          }
          val order = SalesOrderFO(dmo.getId(), dmo.getUserId(), dmo.getCreditTxnId(),
            dmo.getTotalCost(), items.toList, new Date(dmo.getCreateTs()))
          OrderCreated(order)
      }
    }
    
    case class LineItemStatusUpdated(bookId:String, itemNumber:Int, status:LineItemStatus.Value) extends SalesOrderEvent{
      def toDatamodel = Datamodel.LineItemStatusUpdated.newBuilder().
        setStatus(status.toString).
        setBookId(bookId).
        setLineItemNumber(itemNumber).
        build
    }    
    object LineItemStatusUpdated extends DatamodelReader{
      def fromDatamodel = {
        case dm:Datamodel.LineItemStatusUpdated =>
          LineItemStatusUpdated(dm.getBookId(), dm.getLineItemNumber(), LineItemStatus.withName(dm.getStatus()))
      }
    }
  }
}

private[order] object SalesOrderCreateValidator{
  import SalesOrder._
  
  def props = Props[SalesOrderCreateValidator]
  
  sealed trait State
  case object WaitingForRequest extends State
  case object ResolvingDependencies extends State
  case object LookingUpEntities extends State
  case object ChargingCard extends State  
  
  sealed trait Data{
    def inputs:Inputs
  }
  case object NoData extends Data{
    def inputs = Inputs(ActorRef.noSender, null)
  }
  case class Inputs(originator:ActorRef, request:SalesOrder.Command.CreateOrder)
  trait InputsData extends Data{
    def inputs:Inputs
    def originator = inputs.originator 
  }  
  case class UnresolvedDependencies(inputs:Inputs, userUri:Option[JavaURI] = None, 
    inventoryUri:Option[JavaURI] = None, creditUri:Option[JavaURI] = None) extends InputsData

  case class ResolvedDependencies(inputs:Inputs, expectedBooks:Set[String], 
    user:Option[BookstoreUser], books:Map[String, Book], userUri:JavaURI, 
    inventoryUri:JavaURI, creditUri:JavaURI) extends InputsData

  case class LookedUpData(inputs:Inputs, user:BookstoreUser, 
    items:List[SalesOrderLineItemFO], total:Double) extends InputsData 
    
  val ResolveTimeout = 5 seconds
  
  val InvMgmtName = "inventory-management"
  val UserMgmtName = "user-management"
  val CredProcName = "credit-processing"
  
  val InvalidBookIdError = ErrorMessage("order.invalid.bookId", Some("You have supplied an invalid book id"))
  val InvalidUserIdError = ErrorMessage("order.invalid.userId", Some("You have supplied an invalid user id"))
  val CreditRejectedError = ErrorMessage("order.credit.rejected", Some("Your credit card has been rejected"))
  val InventoryNotAvailError = ErrorMessage("order.inventory.notavailable", Some("Inventory for an item on this order is no longer available"))  
}

private[order] class SalesOrderCreateValidator 
  extends BookstoreActor with FSM[SalesOrderCreateValidator.State, SalesOrderCreateValidator.Data] with ImplicitConnectionContext with ServiceConsumer with OrderJsonProtocol{
  import SalesOrderCreateValidator._
  import SalesOrder._
  import SalesOrder.Command._
  import akka.pattern.pipe
  
  import context.dispatcher
  
  startWith(WaitingForRequest, NoData)
  
  when(WaitingForRequest){
    case Event(request:CreateOrder, _) =>      
      lookupService(InvMgmtName).pipeTo(self)
      lookupService(UserMgmtName).pipeTo(self)
      lookupService(CredProcName).pipeTo(self)
      goto(ResolvingDependencies) using UnresolvedDependencies(Inputs(sender(), request))
  }
  
  when(ResolvingDependencies, ResolveTimeout )(transform {
    case Event(ServiceLookupResult(name, uriOpt), data:UnresolvedDependencies) =>
        
      log.info("Resolved dependency {} to: {}", name, uriOpt)
      val newData = name match{
        case InvMgmtName => data.copy(inventoryUri = uriOpt)
        case UserMgmtName => data.copy(userUri = uriOpt)
        case CredProcName => data.copy(creditUri = uriOpt)
      }
      stay using newData
  } using{
    case FSM.State(state, UnresolvedDependencies(inputs, Some(userUri), 
      Some(inventoryUri), Some(creditUri)), _, _, _) =>
        
      log.info("Resolved all dependencies, looking up entities")
      findUserByEmail(userUri, inputs.request.userEmail).pipeTo(self)
            
      val expectedBooks = inputs.request.lineItems.map(_.bookId).toSet
      val bookFutures = expectedBooks.map(id => findBook(inventoryUri, id))
      bookFutures.foreach(_.pipeTo(self))
      goto(LookingUpEntities) using ResolvedDependencies(inputs, expectedBooks, None, Map.empty, inventoryUri, userUri, creditUri)
  })
  
  when(LookingUpEntities, 10 seconds)(transform {
    case Event(b:Book, data:ResolvedDependencies) =>      
      log.info("Looked up book: {}", b) 
      
      //Make sure inventory is available
      val lineItemForBook = data.inputs.request.lineItems.find(_.bookId == b.id)
      lineItemForBook match{
        case None =>
          log.error("Got back a book for which we don't have a line item")
          data.originator ! unexpectedFail
          stop
        
        case Some(item) if item.quantity > b.inventoryAmount  =>
          log.error("Inventory not available for book with id {}", b.id)
          data.originator ! Failure(FailureType.Validation, InventoryNotAvailError )
          stop
          
        case _ =>
          stay using data.copy(books = data.books ++ Map(b.id -> b))    
      }      
      
    case Event(u:BookstoreUser, data:ResolvedDependencies) =>      
      log.info("Found user: {}", u)      
      stay using data.copy(user = Some(u)) 
      
  } using{
    case FSM.State(state, ResolvedDependencies(inputs, expectedBooks, Some(u), 
      bookMap, userUri, invUri, creditUri), _, _, _) 
      if bookMap.keySet == expectedBooks =>            
      
      log.info("Successfully looked up all entities and inventory is available, charging credit card")
      val lineItems = inputs.request.lineItems.
        zipWithIndex.
        flatMap{
          case (item, idx) =>
          bookMap.
            get(item.bookId).
            map(b => SalesOrderLineItemFO(idx + 1, b.id, item.quantity, item.quantity * b.cost, LineItemStatus.Unknown ))
        }
         
      val total = lineItems.map(_.cost).sum
      chargeCreditCard(creditUri, ChargeCreditCard(inputs.request.cardInfo, total)).pipeTo(self)
      goto(ChargingCard) using LookedUpData(inputs, u, lineItems, total)
  }) 
  
  when(ChargingCard, 10 seconds){
    case Event(txn:CreditTxn, data:LookedUpData) if txn.status == "Approved"  =>     
      val order = SalesOrderFO(data.inputs.request.newOrderId, 
        data.user.email, txn.id, data.total, data.items, newDate)
      context.parent.tell(CreateValidatedOrder(order), data.inputs.originator )
      stop            
      
    case Event(txn:CreditTxn, data:LookedUpData) =>
      log.info("Credit was rejected for request: {}", data.inputs.request)
      data.originator ! Failure(FailureType.Validation, CreditRejectedError )
      stop      
  }
  
  whenUnhandled{
    case Event(StateTimeout , data) =>
      log.error("Received state timeout in process to validate an order create request")
      data.inputs.originator ! unexpectedFail
      stop
      
    case Event(other, data) =>
      log.error("Received unexpected message of {} in state {}", other, stateName)
      data.inputs.originator ! unexpectedFail
      stop
  }
  
  def findUserByEmail(uri:JavaURI, email:String):Future[BookstoreUser] = {
    val requestUri = Uri(uri.toString).withPath(Uri.Path("/api") / "user" / email)
    executeHttpRequest[BookstoreUser](HttpRequest(HttpMethods.GET, requestUri))
  }
  
  def findBook(uri:JavaURI, id:String):Future[Book] = {
    val requestUri = Uri(uri.toString).withPath(Uri.Path("/api") / "book" / id)
    executeHttpRequest[Book](HttpRequest(HttpMethods.GET, requestUri))
  }
  
  def chargeCreditCard(uri:JavaURI, charge:ChargeCreditCard):Future[CreditTxn] = {
    import spray.json._    
    val requestUri = Uri(uri.toString).withPath(Uri.Path("/api") / "credit")
    val entity = HttpEntity(ContentTypes.`application/json`, charge.toJson.prettyPrint)
    executeHttpRequest[CreditTxn](HttpRequest(HttpMethods.POST, requestUri, entity = entity))
  }
 
  def unexpectedFail = Failure(FailureType.Service, ServiceResult.UnexpectedFailure )
  def newDate = new Date 
}