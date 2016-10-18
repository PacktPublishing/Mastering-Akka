package com.packt.masteringakka.bookstore.order

import akka.actor._
import com.packt.masteringakka.bookstore.domain.user.FindUserById
import com.packt.masteringakka.bookstore.domain.user.BookstoreUser
import com.packt.masteringakka.bookstore.domain.book.Book
import com.packt.masteringakka.bookstore.domain.book.FindBook
import com.packt.masteringakka.bookstore.domain.user.BookstoreUser
import com.packt.masteringakka.bookstore.domain.credit.ChargeCreditCard
import com.packt.masteringakka.bookstore.domain.user.BookstoreUser
import java.util.Date
import com.packt.masteringakka.bookstore.domain.credit.CreditCardTransaction
import com.packt.masteringakka.bookstore.domain.credit.CreditTransactionStatus
import scala.concurrent.ExecutionContext
import slick.dbio.DBIOAction
import com.packt.masteringakka.bookstore.common._
import java.util.NoSuchElementException
import scala.concurrent.Future

object SalesOrderProcessor{
  def props = Props[SalesOrderProcessor]
  
  sealed trait State
  case object Idle extends State  
  case object ResolvingDependencies extends State
  case object LookingUpEntities extends State
  case object ChargingCard extends State
  case object WritingEntity extends State
  
  sealed trait Data{def originator:ActorRef}
  case class Inputs(originator:ActorRef, request:CreateOrder)
  trait InputsData extends Data{
    def inputs:Inputs
    def originator = inputs.originator 
  }
  case object Uninitialized extends Data {def originator = ActorRef.noSender}  

  case class UnresolvedDependencies(inputs:Inputs, userMgr:Option[ActorRef] = None, 
    bookMgr:Option[ActorRef] = None, creditHandler:Option[ActorRef] = None) extends InputsData

  case class ResolvedDependencies(inputs:Inputs, expectedBooks:Set[Int], 
    user:Option[BookstoreUser], books:Map[Int, Book], userMgr:ActorRef, 
    bookMgr:ActorRef, creditHandler:ActorRef) extends InputsData

  case class LookedUpData(inputs:Inputs, user:BookstoreUser, 
    items:List[SalesOrderLineItem], total:Double) extends InputsData
  
  object ResolutionIdent extends Enumeration{
    val Book, User, Credit = Value
  }
  
  val UserManagerName = "user-manager"
  val CreditHandlerName = "credit-handler"
    
  val InvalidBookIdError = ErrorMessage("order.invalid.bookId", Some("You have supplied an invalid book id"))
  val InvalidUserIdError = ErrorMessage("order.invalid.userId", Some("You have supplied an invalid user id"))
  val CreditRejectedError = ErrorMessage("order.credit.rejected", Some("Your credit card has been rejected"))
  val InventoryNotAvailError = ErrorMessage("order.inventory.notavailable", Some("Inventory for an item on this order is no longer available"))
}

class SalesOrderProcessor extends FSM[SalesOrderProcessor.State, SalesOrderProcessor.Data]{
  import SalesOrderManager._
  import SalesOrderProcessor._
  import concurrent.duration._
  import context.dispatcher
  
  val dao = new SalesOrderProcessorDao
  
  startWith(Idle, Uninitialized)
  
  when(Idle){
    case Event(req:CreateOrder, _) =>
      lookup(BookMgrName ) ! Identify(ResolutionIdent.Book)
      lookup(UserManagerName ) ! Identify(ResolutionIdent.User )
      lookup(CreditHandlerName ) ! Identify(ResolutionIdent.Credit)
      goto(ResolvingDependencies) using UnresolvedDependencies(Inputs(sender(), req))
  }
  
  when(ResolvingDependencies, ResolveTimeout )(transform {
    case Event(ActorIdentity(identifier:ResolutionIdent.Value, actor @ Some(ref)), 
      data:UnresolvedDependencies) =>
        
      log.info("Resolved dependency {}, {}", identifier, ref)
      val newData = identifier match{
        case ResolutionIdent.Book => data.copy(bookMgr = actor)
        case ResolutionIdent.User => data.copy(userMgr = actor)
        case ResolutionIdent.Credit => data.copy(creditHandler = actor)
      }
      stay using newData
  } using{
    case FSM.State(state, UnresolvedDependencies(inputs, Some(user), 
      Some(book), Some(credit)), _, _, _) =>
        
      log.info("Resolved all dependencies, looking up entities")
      user ! FindUserById(inputs.request.userId)
      val expectedBooks = inputs.request.lineItems.map(_.bookId).toSet
      expectedBooks.foreach(id => book ! FindBook(id))
      goto(LookingUpEntities) using ResolvedDependencies(inputs, expectedBooks, None, Map.empty, book, user, credit)
  })
  
  when(LookingUpEntities, 10 seconds)(transform {
    case Event(FullResult(b:Book), data:ResolvedDependencies) =>      
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
      
    case Event(FullResult(u:BookstoreUser), data:ResolvedDependencies) =>      
      log.info("Found user: {}", u)      
      stay using data.copy(user = Some(u)) 
      
    case Event(EmptyResult, data:ResolvedDependencies) => 
      val (etype, error) = 
        if (sender().path.name == BookMgrName) ("book", InvalidBookIdError) 
        else ("user", InvalidUserIdError )
      log.info("Unexpected result type of EmptyResult received looking up a {} entity", etype)
      data.originator ! Failure(FailureType.Validation, error)
      stop
      
  } using{
    case FSM.State(state, ResolvedDependencies(inputs, expectedBooks, Some(u), 
      bookMap, userMgr, bookMgr, creditMgr), _, _, _) 
      if bookMap.keySet == expectedBooks =>            
      
      log.info("Successfully looked up all entities and inventory is available, charging credit card")
      val lineItems = inputs.request.lineItems.
        flatMap{item => 
          bookMap.
            get(item.bookId).
            map(b => SalesOrderLineItem(0, 0, b.id, item.quantity, item.quantity * b.cost, newDate, newDate))
        }
         
      val total = lineItems.map(_.cost).sum
      creditMgr ! ChargeCreditCard(inputs.request.cardInfo, total)      
      goto(ChargingCard) using LookedUpData(inputs, u, lineItems, total)
  })
  
  when(ChargingCard, 10 seconds){
    case Event(FullResult(txn:CreditCardTransaction), data:LookedUpData) if txn.status == CreditTransactionStatus.Approved  =>
      import akka.pattern.pipe      
      val order = SalesOrder(0, data.user.id, txn.id, SalesOrderStatus.InProgress, data.total, data.items, newDate, newDate)
      dao.createSalesOrder(order) pipeTo self
      goto(WritingEntity) using data
      
    case Event(FullResult(txn:CreditCardTransaction), data:LookedUpData) =>
      log.info("Credit was rejected for request: {}", data.inputs.request)
      data.originator ! Failure(FailureType.Validation, CreditRejectedError )
      stop      
  }
  
  when(WritingEntity, 5 seconds){
    case Event(ord:SalesOrder, data:LookedUpData) =>
      log.info("Successfully created new sales order: {}", ord)
      data.originator ! FullResult(ord)
      stop

    case Event(Status.Failure(ex:SalesOrderProcessorDao.InventoryNotAvailaleException), data:LookedUpData) =>
      log.error(ex, "DB write failed because inventory for an item was not available when performing db writes")
      data.originator ! Failure(FailureType.Validation, InventoryNotAvailError )
      stop                  
      
    case Event(Status.Failure(ex), data:LookedUpData) =>
      log.error(ex, "Error creating a new sales order")
      data.originator ! unexpectedFail
      stop
  }
  
  whenUnhandled{
    case e @ Event(StateTimeout, data) =>
      log.error("State timeout when in state {}", stateName)
      data.originator ! unexpectedFail
      stop
      
    case e @ Event(other, data) =>
      log.error("Unexpected result of {} when in state {}", other, stateName)
      data.originator ! unexpectedFail
      stop      
  }
  
  def newDate = new Date
  def lookup(name:String) = context.actorSelection(s"/user/$name")
  def unexpectedFail = Failure(FailureType.Service, ServiceResult.UnexpectedFailure )
}

object SalesOrderProcessorDao{
  class InventoryNotAvailaleException extends Exception
}

class SalesOrderProcessorDao(implicit ec:ExecutionContext) extends BookstoreDao{
  import slick.driver.PostgresDriver.api._
  import DaoHelpers._
  import SalesOrderProcessorDao._
  
  def createSalesOrder(order:SalesOrder) = {
    val insertHeader = sqlu"""
      insert into SalesOrderHeader (userId, creditTxnId, status, totalCost, createTs, modifyTs)
      values (${order.userId}, ${order.creditTxnId}, ${order.status.toString}, ${order.totalCost}, ${order.createTs.toSqlDate}, ${order.modifyTs.toSqlDate})
    """
      
    val getId = lastIdSelect("salesorderheader")
    
    def insertLineItems(orderId:Int) = order.lineItems.map{ item =>
      val insert = 
        sqlu"""
          insert into SalesOrderLineItem (orderId, bookId, quantity, cost, createTs, modifyTs)
          values ($orderId, ${item.bookId}, ${item.quantity}, ${item.cost}, ${item.createTs.toSqlDate}, ${item.modifyTs.toSqlDate})
        """
      
      //Using optimistic currency control on the update via the where clause
      val decrementInv = 
        sqlu"""
          update Book set inventoryAmount = inventoryAmount - ${item.quantity} where id = ${item.bookId} and inventoryAmount >= ${item.quantity}        
        """
          
      insert.
        andThen(decrementInv).
        filter(_ == 1)
    }
    
    
    val txn = 
      for{
        _ <- insertHeader
        id <- getId
        if id.headOption.isDefined
        _ <- DBIOAction.sequence(insertLineItems(id.head))
      } yield{
        order.copy(id = id.head)
      }
      
    db.
      run(txn.transactionally).
      recoverWith{
        case ex:NoSuchElementException => Future.failed(new InventoryNotAvailaleException)
      }
  }
}