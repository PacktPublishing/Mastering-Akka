package com.packt.masteringakka.bookstore.order

import java.util.Date
import akka.actor.ActorRef
import com.packt.masteringakka.bookstore.user.BookstoreUserFO
import com.packt.masteringakka.bookstore.inventory.BookFO
import akka.actor.Identify
import concurrent.duration._
import akka.actor.ActorIdentity
import akka.actor.FSM
import com.packt.masteringakka.bookstore.user.CustomerRelationsManager
import com.packt.masteringakka.bookstore.inventory.InventoryClerk
import com.packt.masteringakka.bookstore.common._
import com.packt.masteringakka.bookstore.credit.CreditTransactionStatus
import akka.actor.Props
import com.packt.masteringakka.bookstore.credit.CreditCardInfo
import com.packt.masteringakka.bookstore.common.EntityFieldsObject
import com.packt.masteringakka.bookstore.credit.CreditCardTransactionFO
import com.packt.masteringakka.bookstore.credit.CreditAssociate

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
class SalesOrder(id:String) extends PersistentEntity[SalesOrderFO](id){
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
      
    case UpdateLineItemStatus(bookId, status) =>
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
  def props(id:String) = Props(classOf[SalesOrder], id)
  
  case class LineItemRequest(bookId:String, quantity:Int)
  
  object Command{
    case class CreateOrder(newOrderId:String, userEmail:String, lineItems:List[LineItemRequest], cardInfo:CreditCardInfo) 
    case class CreateValidatedOrder(order:SalesOrderFO)
    case class UpdateLineItemStatus(bookId:String, status:LineItemStatus.Value)    
  }

  object Event{
    trait SalesOrderEvent extends EntityEvent{def entityType = EntityType }
    case class OrderCreated(order:SalesOrderFO) extends SalesOrderEvent with InventoryClerk.SalesOrderCreateInfo{
      def id = order.id
      def lineItemInfo = order.lineItems.map{ item =>
        (item.bookId, item.quantity )
      }
      
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
  case class UnresolvedDependencies(inputs:Inputs, userAssociate:Option[ActorRef] = None, 
    invClerk:Option[ActorRef] = None, creditAssociate:Option[ActorRef] = None) extends InputsData

  case class ResolvedDependencies(inputs:Inputs, expectedBooks:Set[String], 
    user:Option[BookstoreUserFO], books:Map[String, BookFO], userAssociate:ActorRef, 
    invClerk:ActorRef, creditAssociate:ActorRef) extends InputsData

  case class LookedUpData(inputs:Inputs, user:BookstoreUserFO, 
    items:List[SalesOrderLineItemFO], total:Double) extends InputsData
  
  object ResolutionIdent extends Enumeration{
    val Book, User, Credit = Value
  }  
    
  val ResolveTimeout = 5 seconds
  
  val InvalidBookIdError = ErrorMessage("order.invalid.bookId", Some("You have supplied an invalid book id"))
  val InvalidUserIdError = ErrorMessage("order.invalid.userId", Some("You have supplied an invalid user id"))
  val CreditRejectedError = ErrorMessage("order.credit.rejected", Some("Your credit card has been rejected"))
  val InventoryNotAvailError = ErrorMessage("order.inventory.notavailable", Some("Inventory for an item on this order is no longer available"))  
}

private[order] class SalesOrderCreateValidator extends FSM[SalesOrderCreateValidator.State, SalesOrderCreateValidator.Data]{
  import SalesOrderCreateValidator._
  import SalesOrder.Command._
  
  startWith(WaitingForRequest, NoData)
  
  when(WaitingForRequest){
    case Event(request:CreateOrder, _) =>
      lookup(InventoryClerk.Name) ! Identify(ResolutionIdent.Book)
      lookup(CustomerRelationsManager.Name) ! Identify(ResolutionIdent.User )
      lookup(CreditAssociate.Name) ! Identify(ResolutionIdent.Credit)
      goto(ResolvingDependencies) using UnresolvedDependencies(Inputs(sender(), request))
  }
  
  when(ResolvingDependencies, ResolveTimeout )(transform {
    case Event(ActorIdentity(identifier:ResolutionIdent.Value, actor @ Some(ref)), 
      data:UnresolvedDependencies) =>
        
      log.info("Resolved dependency {}, {}", identifier, ref)
      val newData = identifier match{
        case ResolutionIdent.Book => data.copy(invClerk = actor)
        case ResolutionIdent.User => data.copy(userAssociate = actor)
        case ResolutionIdent.Credit => data.copy(creditAssociate = actor)
      }
      stay using newData
  } using{
    case FSM.State(state, UnresolvedDependencies(inputs, Some(userAssociate), 
      Some(invClerk), Some(creditAssociate)), _, _, _) =>
        
      log.info("Resolved all dependencies, looking up entities")
      userAssociate ! CustomerRelationsManager.FindUserByEmail(inputs.request.userEmail)
      val expectedBooks = inputs.request.lineItems.map(_.bookId).toSet
      expectedBooks.foreach(id => invClerk ! InventoryClerk.FindBook(id))
      goto(LookingUpEntities) using ResolvedDependencies(inputs, expectedBooks, None, Map.empty, invClerk, userAssociate, creditAssociate)
  })
  
  when(LookingUpEntities, 10 seconds)(transform {
    case Event(FullResult(b:BookFO), data:ResolvedDependencies) =>      
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
      
    case Event(FullResult(u:BookstoreUserFO), data:ResolvedDependencies) =>      
      log.info("Found user: {}", u)      
      stay using data.copy(user = Some(u)) 
      
    case Event(EmptyResult, data:ResolvedDependencies) => 
      val (etype, error) = 
        if (sender().path.name.contains("book")) ("book", InvalidBookIdError) 
        else ("user", InvalidUserIdError )
      log.info("Unexpected result type of EmptyResult received looking up a {} entity", etype)
      data.originator ! Failure(FailureType.Validation, error)
      stop
      
  } using{
    case FSM.State(state, ResolvedDependencies(inputs, expectedBooks, Some(u), 
      bookMap, userAssociate, invClerk, creditAssociate), _, _, _) 
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
      creditAssociate ! CreditAssociate.ChargeCreditCard(inputs.request.cardInfo, total)      
      goto(ChargingCard) using LookedUpData(inputs, u, lineItems, total)
  }) 
  
  when(ChargingCard, 10 seconds){
    case Event(FullResult(txn:CreditCardTransactionFO), data:LookedUpData) if txn.status == CreditTransactionStatus.Approved  =>     
      val order = SalesOrderFO(data.inputs.request.newOrderId, 
        data.user.id, txn.id, data.total, data.items, newDate)
      context.parent.tell(CreateValidatedOrder(order), data.inputs.originator )
      stop            
      
    case Event(FullResult(txn:CreditCardTransactionFO), data:LookedUpData) =>
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
  
  def unexpectedFail = Failure(FailureType.Service, ServiceResult.UnexpectedFailure )
  def newDate = new Date
  def lookup(name:String) = context.actorSelection(s"/user/$name")  
}