package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common.EntityRepository
import scala.concurrent.Future
import concurrent.ExecutionContext
import slick.dbio.DBIOAction
import slick.jdbc.GetResult

object SalesOrderRepository{
  val OrderSelect = "select id, userId, creditTxnId, status, totalCost, createTs, modifyTs from SalesOrderHeader where"
  val LineItemSelect = "select id, orderId, bookId, quantity, cost, createTs, modifyTs from SalesOrderLineItem where "
  implicit val GetOrder = GetResult{r => SalesOrderFO(r.<<, r.<<, r.<<, SalesOrderStatus.withName(r.<<), r.<<, Nil, r.nextTimestamp, r.nextTimestamp)}
  implicit val GetLineItem = GetResult{r => SalesOrderLineItemFO(r.<<, r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp)}  
}

class SalesOrderRepository(implicit ex:ExecutionContext) extends EntityRepository[SalesOrderFO]{
  import slick.driver.PostgresDriver.api._
  import RepoHelpers._
  import SalesOrderRepository._  
  
  def loadEntity(id:Int) = {
    val headersF = db.run(sql"#$OrderSelect id = $id".as[SalesOrderFO])
    val itemsF = db.run(sql"#$LineItemSelect orderId = $id".as[SalesOrderLineItemFO])
        
    for{
      header <- headersF
      items <- itemsF
    } yield{
      header.headOption.map(_.copy(lineItems = items.toList))      
    }    
  }
  
  def persistEntity(order:SalesOrderFO) = {
    val insertHeader = sqlu"""
      insert into SalesOrderHeader (userId, creditTxnId, status, totalCost, createTs, modifyTs)
      values (${order.userId}, ${order.creditTxnId}, ${order.status.toString}, ${order.totalCost}, ${order.createTs.toSqlDate}, ${order.modifyTs.toSqlDate})
    """
      
    val getId = lastIdSelect("salesorderheader")
    
    def insertLineItems(orderId:Int) = order.lineItems.map{ item => 
      sqlu"""
        insert into SalesOrderLineItem (orderId, bookId, quantity, cost, createTs, modifyTs)
        values ($orderId, ${item.bookId}, ${item.quantity}, ${item.cost}, ${item.createTs.toSqlDate}, ${item.modifyTs.toSqlDate})
      """      
    }
    
    
    val txn = 
      for{
        _ <- insertHeader
        id <- getId
        if id.headOption.isDefined
        _ <- DBIOAction.sequence(insertLineItems(id.head))
      } yield{
        id.head
      }
      
    db.run(txn.transactionally)   
  }
  
  /**
   * Finds orders ids tied to a specific user by id
   * @param userId The id of the user to find orders for
   * @return a Future wrapping a Vector of Int order ids
   */
  def findOrderIdsForUser(userId:Int) = {
    val select = sql"select id from SalesOrderHeader where userId = $userId".as[Int]
    db.run(select)
  } 
  
  /**
   * Finds orders ids that have a line item for the supplied book id
   * @param bookId The id of the book to find orders for
   * @return a Future wrapping a Vector of Int order ids
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
    val select = sql"select distinct(l.orderId) from SalesOrderLineItem l left join BookTag t on l.bookId = t.bookId where t.tag = $tag"
    db.run(select.as[Int])
  }  
  
  def updateOrderStatus(id:Int, status:SalesOrderStatus.Value) = {
    db.run(sqlu"update SalesOrderHeader set status = ${status.toString()} where id = $id")
  }
  
  //Not currently handled...
  def deleteEntity(id:Int) = Future.failed(new NotImplementedError("delete not implemented for sales order"))
}