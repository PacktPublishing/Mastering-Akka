package com.packt.masteringakka.bookstore.order

import akka.actor._
import com.packt.masteringakka.bookstore.common.BookStoreActor
import slick.jdbc.GetResult
import com.packt.masteringakka.bookstore.common.BookstoreDao
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import concurrent.duration._

/**
 * Companion to the SalesOrderManager service
 */
object SalesOrderManager{
  val Name = "order-manager"
  def props = Props[SalesOrderManager]
  
  val BookMgrName = "book-manager" 
  val ResolveTimeout = 5 seconds
}

/**
 * Service for performing actions related to sales orders
 */
class SalesOrderManager extends BookStoreActor{
  import SalesOrderManager._
  import context.dispatcher
  val dao = new SalesOrderManagerDao
  
  //TODO: Refactor to a bad future driven actor and update chapter 1
  def receive = {
    case FindOrderById(id) =>
      pipeResponse(dao.findOrderById(id))
      
    case FindOrdersForUser(userId) =>
      pipeResponse(dao.findOrdersForUser(userId))
      
    case FindOrdersForBook(bookId) =>
      val result = findForBook(dao.findOrderIdsForBook(bookId))         
      pipeResponse(result)
      
    case FindOrdersForBookTag(tag) =>
      val result = findForBook(dao.findOrderIdsForBookTag(tag))         
      pipeResponse(result)      
    
    case req:CreateOrder =>
      log.info("Creating new sales order processor and forwarding request")
      val proc = context.actorOf(SalesOrderProcessor.props)
      proc forward req      
  }
  
  /**
   * Does a lookup of orders using information from the books tied to the orders
   * @param f A function that returns a Future for the ids of the orders to lookup
   * @return a Future for a Vector of SalesOrder
   */
  def findForBook(f: => Future[Vector[Int]]) = {
    for{
      orderIds <- f
      orders <- dao.findOrdersByIds(orderIds.toSet)
    } yield orders    
  }
}

/**
 * Companion to the SalesOrderManagerDao
 */
object SalesOrderManagerDao{
  val BaseSelect = "select id, userId, creditTxnId, status, totalCost, createTs, modifyTs from SalesOrderHeader where"
  implicit val GetOrder = GetResult{r => SalesOrder(r.<<, r.<<, r.<<, SalesOrderStatus.withName(r.<<), r.<<, Nil, r.nextTimestamp, r.nextTimestamp)}
  implicit val GetLineItem = GetResult{r => SalesOrderLineItem(r.<<, r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp)}
}

/**
 * Dao class for sales order DB actions for Postgres
 */
class SalesOrderManagerDao(implicit ec:ExecutionContext) extends BookstoreDao{
  import SalesOrderManagerDao._
  import slick.driver.PostgresDriver.api._
  import slick.jdbc.SQLActionBuilder
  
  /**
   * Finds a single order by id
   * @param id The id of the order to find
   * @return a Future wrapping an optional SalesOrder
   */
  def findOrderById(id:Int) = findOrdersByIds(Set(id)).map(_.headOption)
  
  /**
   * Finds a Vector of orders by their ids
   * @param ids The ids of the orders to find
   * @return a Future wrapping an Vector of SalesOrder
   */  
  def findOrdersByIds(ids:Set[Int]) = {
    if (ids.isEmpty) Future.successful(Vector.empty)
    else{
      val idsInput = ids.mkString(",")
      val select = sql"#$BaseSelect id in (#$idsInput)"
      findOrdersByCriteria(select)      
    }
  } 
  
  /**
   * Uses a supplied select statement to find orders and the line items for those orders
   * @param orderSelect a select that will return a Vector of SalesOrder
   * @return a Future wrapping a Vector of SalesOrder
   */  
  private def findOrdersByCriteria(orderSelect:SQLActionBuilder) = {
    val headersF = db.run(orderSelect.as[SalesOrder])
    def selectItems(orderIds:Seq[Int]) = {
      if (orderIds.isEmpty) Future.successful(Vector.empty)
      else db.run(sql"select id, orderId, bookId, quantity, cost, createTs, modifyTs from SalesOrderLineItem where orderId in (#${orderIds.mkString(",")})".as[SalesOrderLineItem])
    }
    for{
      headers <- headersF
      items <- selectItems(headers.map(_.id))
    } yield{
      val itemsByOrder = items.groupBy(_.orderId)
      headers.map(o => o.copy(lineItems = itemsByOrder.get(o.id).map(_.toList).getOrElse(Nil)))
    }
  }   
  
  /**
   * Finds orders tied to a specific user by id
   * @param userId The id of the user to find orders for
   * @return a Future wrapping a Vector of SalesOrder
   */
  def findOrdersForUser(userId:Int) = {
    val select = sql"#$BaseSelect userId = $userId"
    findOrdersByCriteria(select)
  }

  /**
   * Finds orders ids that have a line item for the supplied book id
   * @param bookId The id of the book to find orders for
   * @return a Future wrapping a Vector of Int order ods
   */  
  def findOrderIdsForBook(bookId:Int) = {
    val select = sql"select distinct(orderId) from SalesOrderLineItem where bookId = $bookId"
    db.run(select.as[Int])
  }
  
  /**
   * Finds orders ids that have a line item for a book with the supplied tag
   * @param tag The tag on the book to find orders for
   * @return a Future wrapping a Vector of Int order ids
   */   
  def findOrderIdsForBookTag(tag:String) = {
    val select = sql"select distinct(l.orderId) from SalesOrderLineItem l right join BookTag t on l.bookId = t.bookId where t.tag = $tag"
    db.run(select.as[Int])
  }  
}

