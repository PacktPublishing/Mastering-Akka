package com.packt.masteringakka.bookstore.order

import java.util.Date
import com.packt.masteringakka.bookstore.domain.credit.CreditCardInfo

//Persistent entities
object SalesOrderStatus extends Enumeration{
  val InProgress, Shipped, Cancelled = Value
}
case class SalesOrder(id:Int, userId:Int, creditTxnId:Int, status:SalesOrderStatus.Value, totalCost:Double, lineItems:List[SalesOrderLineItem], createTs:Date, modifyTs:Date)
case class SalesOrderLineItem(id:Int, orderId:Int, bookId:Int, quantity:Int, cost:Double, createTs:Date,  modifyTs:Date)

//Lookup requests
case class FindOrderById(id:Int)
case class FindOrdersForBook(bookId:Int)
case class FindOrdersForUser(userId:Int)
case class FindOrdersForBookTag(tag:String)

//Create/Modify requests
case class LineItemRequest(bookId:Int, quantity:Int)
case class CreateOrder(userId:Int, lineItems:List[LineItemRequest], cardInfo:CreditCardInfo)