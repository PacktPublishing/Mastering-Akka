package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common._
import akka.actor._
import akka.persistence.query.PersistenceQuery
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.stream.ActorMaterializer
import java.util.Date
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.json4s.JObject
import com.packt.masteringakka.bookstore.common.ViewBuilder
import akka.persistence.query.EventEnvelope

trait BookReadModel{
  def indexRoot = "inventory"
  def entityType = Book.EntityType 
}

object BookViewBuilder{
  val Name = "book-view-builder"
  case class BookRM(id:String, title:String, author:String, tags:List[String], cost:Double, 
    inventoryAmount:Int, createTs:Date, deleted:Boolean = false) extends ReadModelObject 
  def props = Props[BookViewBuilder]
}

class BookViewBuilder extends BookReadModel with ViewBuilder[BookViewBuilder.BookRM]{
  import ViewBuilder._
  import BookViewBuilder._
  import Book.Event._
  import context.dispatcher

  def projectionId = "book-view-builder"
  
  def actionFor(bookId:String, env:EventEnvelope) = env.event match {
    case BookCreated(book) =>
      log.info("Saving a new book entity into the elasticsearch index: {}", book)
      val bookRM = BookRM(book.id, book.title, book.author, book.tags, book.cost, book.inventoryAmount,
        book.createTs, book.deleted )      
      InsertAction(book.id, bookRM)
      
    case TagAdded(tag) =>
      UpdateAction(bookId, "tags += tag", Map("tag" -> tag))
      
    case TagRemoved(tag) =>
      UpdateAction(bookId, "tags -= tag", Map("tag" -> tag))      
      
    case InventoryAdded(amount) =>
      UpdateAction(bookId, "inventoryAmount += amount", Map("amount" -> amount))
      
    case InventoryAllocated(orderId, bookId, amount) =>
      UpdateAction(bookId, "inventoryAmount -= amount", Map("amount" -> amount))  
                
    case BookDeleted(bookId) =>
      UpdateAction(bookId, "deleted = delBool", Map("delBool" -> true))
      
    case InventoryBackordered(orderId, bookId) =>
      NoAction(bookId)
  }
}


object BookView{
  val Name = "book-view"
  case class FindBooksByTags(tags:Seq[String])
  case class FindBooksByAuthor(author:String)  
  def props = Props[BookView]
}

class BookView extends BookReadModel with BookstoreActor with ElasticsearchSupport{
  import BookView._
  import ElasticsearchApi._
  import context.dispatcher
  
  def receive = {
    case FindBooksByAuthor(author) =>
      val results = queryElasticsearch(s"author:$author")
      pipeResponse(results)
      
    case FindBooksByTags(tags) =>
      val query = tags.map(t => s"tags:$t").mkString(" AND ")
      val results = queryElasticsearch(query)
      pipeResponse(results)      
  }
}