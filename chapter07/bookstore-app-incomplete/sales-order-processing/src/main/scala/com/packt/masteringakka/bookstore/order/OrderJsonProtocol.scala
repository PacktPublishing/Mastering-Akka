package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common.BookstoreJsonProtocol
import spray.json._
import SalesAssociate._
import SalesOrder._
import SalesOrderViewBuilder._
import com.packt.masteringakka.bookstore.credit.CreditCardInfo

/**
 * Json protocol class for the order system
 */
trait OrderJsonProtocol extends BookstoreJsonProtocol{
  implicit object LineItemStatusFormatter extends RootJsonFormat[LineItemStatus.Value]{
    def write(status:LineItemStatus.Value) = {
      JsString(status.toString)
    }
    def read(jv:JsValue) = jv match{
      case JsString(s) => LineItemStatus.withName(s)
      case other => throw new DeserializationException(s"expected JsString but got $other")
    }
  }
  implicit val lineItemFoFormat = jsonFormat5(SalesOrderLineItemFO) 
  implicit val orderFoFormat = jsonFormat7(SalesOrderFO.apply)
  implicit val lineItemBookFormat = jsonFormat4(LineItemBook)
  implicit val salesOrderLineItemFormat = jsonFormat5(SalesOrderLineItem)
  implicit val orderRmFormat = jsonFormat7(SalesOrderRM)
  
  implicit val ccInfoFormat = jsonFormat4(CreditCardInfo)
  implicit val lineItemReqFormat = jsonFormat2(LineItemRequest)
  implicit val createNewOrderFormat = jsonFormat3(CreateNewOrder)
}