package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.DatamodelReader

object Book{ 

  object Event{    
    case class InventoryAllocated(orderId:String, bookId:String, amount:Int)
    object InventoryAllocated extends DatamodelReader{
      def fromDatamodel = {
        case ia:Datamodel.InventoryAllocated =>
          InventoryAllocated(ia.getOrderId(), ia.getBookId(), ia.getAmount())
      }
    }
    
    case class InventoryBackordered(orderId:String, bookId:String)
    object InventoryBackordered extends DatamodelReader{
      def fromDatamodel = {
        case ib:Datamodel.InventoryBackordered =>
          InventoryBackordered(ib.getOrderId(), ib.getBookId())
      }
    }
  }
}