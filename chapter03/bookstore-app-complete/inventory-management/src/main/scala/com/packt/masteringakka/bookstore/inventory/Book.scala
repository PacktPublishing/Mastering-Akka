package com.packt.masteringakka.bookstore.inventory

import akka.actor.Props
import akka.actor.Stash
import java.util.Date
import akka.actor.ReceiveTimeout
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.packt.masteringakka.bookstore.common.EntityFieldsObject
import com.packt.masteringakka.bookstore.common.EntityActor
import com.packt.masteringakka.bookstore.common.FailureType
import com.packt.masteringakka.bookstore.common.Failure
import com.packt.masteringakka.bookstore.common.ErrorMessage

/**
 * Value object representation of a Book
 */
case class BookFO(id:Int, title:String, author:String, tags:List[String], cost:Double, 
  inventoryAmount:Int, createTs:Date, modifyTs:Date, deleted:Boolean = false) extends EntityFieldsObject[BookFO]{
  def assignId(id:Int) = this.copy(id = id)
  def markDeleted = this.copy(deleted = true)
}

/**
 * Companion to the Book entity where the vocab is defined
 */
private [bookstore] object Book{
  case class AddTag(tag:String)
  case class RemoveTag(tag:String)
  case class AddInventory(amount:Int)
  case class AllocateInventory(amount:Int)

  def props(id:Int) = Props(classOf[Book], id)
  val InventoryNotAvailError = ErrorMessage("inventory.notavailable", Some("Inventory for an item on an order can not be allocated"))
}

/**
 * Entity class representing a Book within the bookstore app
 */
private[inventory] class Book(idInput:Int) extends EntityActor[BookFO](idInput){
  import Book._
  import concurrent.duration._
  import context.dispatcher
  import akka.pattern.pipe
  import EntityActor._
  
  val repo = new BookRepository
  val errorMapper:ErrorMapper = {
    case ex:BookRepository.InventoryNotAvailableException =>
      Failure(FailureType.Validation, InventoryNotAvailError )
  }
  
  def initializedHandling:StateFunction = {
    case Event(AddTag(tag), InitializedData(fo: BookFO)) =>
      requestFoForSender
      if (fo.tags.contains(tag)){
        log.info("Not adding tag {} to book {}, tag already exists", tag, fo.id)
        stay
      }
      else
        persist(fo, repo.tagBook(fo.id, tag), _ => fo.copy(tags =  fo.tags :+ tag))      
            
    case Event(RemoveTag(tag), InitializedData(fo: BookFO)) =>
      requestFoForSender
      persist(fo, repo.untagBook(fo.id, tag), _ => fo.copy(tags = fo.tags.filterNot( _ == tag)))      
      
    case Event(AddInventory(amount:Int), InitializedData(fo: BookFO)) =>
      requestFoForSender
      persist(fo, repo.addInventoryToBook(fo.id, amount), 
        _ => fo.copy(inventoryAmount = fo.inventoryAmount + amount)) 
      
    case Event(AllocateInventory(amount), InitializedData(fo: BookFO)) =>
      requestFoForSender
      persist(fo, repo.allocateInventory(fo.id, amount), _ => fo.copy(inventoryAmount = fo.inventoryAmount - amount))       
  }
}