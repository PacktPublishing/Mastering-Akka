package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.BookstoreJsonProtocol
import BookViewBuilder._
import InventoryClerk._

/**
 * Json protocol class for the inventory system
 */
trait InventoryJsonProtocol extends BookstoreJsonProtocol{
  implicit val bookFoFormat = jsonFormat8(BookFO.apply)
  implicit val bookRmFormat = jsonFormat8(BookRM.apply)
  implicit val catalogBookFormat = jsonFormat4(CatalogNewBook)
}