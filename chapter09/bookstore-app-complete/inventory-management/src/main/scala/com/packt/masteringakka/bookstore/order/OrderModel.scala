package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common.EntityEvent
import com.packt.masteringakka.bookstore.common.DatamodelReader

case class OrderLineItem(bookId:String, quantity:Int)
case class SalesOrder(id:String, lineItems:List[OrderLineItem])

object SalesOrder{
  import collection.JavaConversions._
  
  object Event{
    case class OrderCreated(order:SalesOrder)
    object OrderCreated extends DatamodelReader{
      def fromDatamodel = {
        case doc:Datamodel.OrderCreated =>
          val dmo = doc.getOrder()
          val items = dmo.getLineItemList().map{ item =>
            OrderLineItem(item.getBookId(), item.getQuantity())
          }
          val order = SalesOrder(dmo.getId(), items.toList)
          OrderCreated(order)
      }
    }
  }
}