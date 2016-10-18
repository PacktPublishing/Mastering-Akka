package com.packt.masteringakka.bookstore.order

import java.util.Date
import com.packt.masteringakka.bookstore.common.EntityActor
import akka.actor.ActorRef
import com.packt.masteringakka.bookstore.user.BookstoreUserFO
import com.packt.masteringakka.bookstore.inventory.BookFO
import akka.actor.Identify
import concurrent.duration._
import akka.actor.ActorIdentity
import akka.actor.FSM
import com.packt.masteringakka.bookstore.user.CustomerRelationsManager
import com.packt.masteringakka.bookstore.inventory.InventoryClerk
import com.packt.masteringakka.bookstore.common.FullResult
import com.packt.masteringakka.bookstore.common.Failure
import com.packt.masteringakka.bookstore.common.FailureType
import com.packt.masteringakka.bookstore.common.ServiceResult
import com.packt.masteringakka.bookstore.common.ErrorMessage
import com.packt.masteringakka.bookstore.common.EmptyResult
import com.packt.masteringakka.bookstore.credit.CreditTransactionStatus
import akka.actor.Props
import com.packt.masteringakka.bookstore.credit.CreditCardInfo
import com.packt.masteringakka.bookstore.common.EntityFieldsObject
import com.packt.masteringakka.bookstore.credit.CreditCardTransactionFO
import com.packt.masteringakka.bookstore.credit.CreditAssociate

object SalesOrderStatus extends Enumeration{
  val InProgress, Approved, BackOrdered, Cancelled = Value
}
case class SalesOrderLineItemFO(id:Int, orderId:Int, bookId:Int, quantity:Int, cost:Double, createTs:Date,  modifyTs:Date)

case class SalesOrderFO(id:Int, userId:Int, creditTxnId:Int, 
    status:SalesOrderStatus.Value, totalCost:Double, 
    lineItems:List[SalesOrderLineItemFO], createTs:Date, modifyTs:Date, deleted:Boolean = false) extends EntityFieldsObject[SalesOrderFO]{
    def assignId(id:Int) = this.copy(id = id)
    def markDeleted = this.copy(deleted = true)
}

/**
 * Companion to the SalesOrder aggregate root entity
 */
object SalesOrder{
  import EntityActor._
  
  def props(id:Int) = Props(classOf[SalesOrder], id)
  
  case class LineItemRequest(bookId:Int, quantity:Int)
  case class CreateOrder(userId:Int, lineItems:List[LineItemRequest], cardInfo:CreditCardInfo)  
  case class UpdateOrderStatus(status:SalesOrderStatus.Value)
  
  case object ResolvingDependencies extends State
  case object LookingUpEntities extends State
  case object ChargingCard extends State
  
  case class Inputs(originator:ActorRef, request:CreateOrder)
  trait InputsData extends Data{
    def inputs:Inputs
    def originator = inputs.originator 
  }  
  case class UnresolvedDependencies(inputs:Inputs, userAssociate:Option[ActorRef] = None, 
    invClerk:Option[ActorRef] = None, creditAssociate:Option[ActorRef] = None) extends InputsData

  case class ResolvedDependencies(inputs:Inputs, expectedBooks:Set[Int], 
    user:Option[BookstoreUserFO], books:Map[Int, BookFO], userAssociate:ActorRef, 
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

/**
 * Aggregate root for the SalesOrder and SalesOrderLineItem entities
 */
class SalesOrder(idInput:Int) extends EntityActor[SalesOrderFO](idInput){
  import SalesOrder._
  import SalesOrderRepository._
  import EntityActor._
  import context.dispatcher
  
  val repo = new SalesOrderRepository
  val errorMapper:ErrorMapper = PartialFunction.empty
  
  override def customCreateHandling:StateFunction = {
    case Event(req:CreateOrder, _) =>
      lookup(InventoryClerk.Name  ) ! Identify(ResolutionIdent.Book)
      lookup(CustomerRelationsManager.Name) ! Identify(ResolutionIdent.User )
      lookup(CreditAssociate.Name ) ! Identify(ResolutionIdent.Credit)
      goto(ResolvingDependencies) using UnresolvedDependencies(Inputs(sender(), req))      
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
      userAssociate ! CustomerRelationsManager.FindUserById(inputs.request.userId)
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
        if (sender().path.name == InventoryClerk.Name ) ("book", InvalidBookIdError) 
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
        flatMap{item => 
          bookMap.
            get(item.bookId).
            map(b => SalesOrderLineItemFO(0, 0, b.id, item.quantity, item.quantity * b.cost, newDate, newDate))
        }
         
      val total = lineItems.map(_.cost).sum
      creditAssociate ! CreditAssociate.ChargeCreditCard(inputs.request.cardInfo, total)      
      goto(ChargingCard) using LookedUpData(inputs, u, lineItems, total)
  }) 
  
  when(ChargingCard, 10 seconds){
    case Event(FullResult(txn:CreditCardTransactionFO), data:LookedUpData) if txn.status == CreditTransactionStatus.Approved  =>           
      val order = SalesOrderFO(0, data.user.id, txn.id, SalesOrderStatus.InProgress, data.total, data.items, newDate, newDate)
      requestFoForSender(data.inputs.originator)
      persist(order, repo.persistEntity(order), id => order.copy(id = id), true)
      
    case Event(FullResult(txn:CreditCardTransactionFO), data:LookedUpData) =>
      log.info("Credit was rejected for request: {}", data.inputs.request)
      data.originator ! Failure(FailureType.Validation, CreditRejectedError )
      stop      
  }  
  
  override def postCreate(fo:SalesOrderFO){
    val items = fo.lineItems.map(i => (i.bookId, i.quantity ))
    val event = InventoryClerk.OrderCreated(fo.id, items)
    context.system.eventStream.publish(event)
  }
  
  def initializedHandling:StateFunction = {
    case Event(UpdateOrderStatus(status), data:InitializedData[SalesOrderFO]) =>
      log.info("Setting status on order {} to {}", data.fo.id, status)
      persist(data.fo, repo.updateOrderStatus(data.fo.id, status), _ => data.fo.copy(status = status))
  }
  
  def unexpectedFail = Failure(FailureType.Service, ServiceResult.UnexpectedFailure )
  def newDate = new Date
  def lookup(name:String) = context.actorSelection(s"/user/$name")  
}