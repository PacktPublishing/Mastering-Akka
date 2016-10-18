package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.BookstoreRepository
import slick.jdbc.GetResult
import scala.concurrent.ExecutionContext
import com.packt.masteringakka.bookstore.common.EntityRepository
import scala.concurrent.Future

/**
 * Companion to the BookRepository class
 */
object BookRepository{
  implicit val GetBook = GetResult{r => BookFO(r.<<, r.<<, r.<<, r.nextString.split(",").filter(_.nonEmpty).toList, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp)}
  class InventoryNotAvailableException extends Exception
}

/**
 * DDD repository class for accessing the persistent storage of books
 */
class BookRepository(implicit ec:ExecutionContext) extends EntityRepository[BookFO]{
  import slick.driver.PostgresDriver.api._  
  import BookRepository._
  import RepoHelpers._
  import slick.dbio._
  
  /**
   * Finds the ids of books that have all of the supplied tags on them
   * @param tags The tags that the books must have all of
   * @return a Future for a Vector[Int] which is the ids of the matching books
   */
  def findBookIdsByTags(tags:Seq[String]) = {
    val tagsParam = tags.map(t => s"'${t.toLowerCase}'").mkString(",")      
    val idsWithAllTags = db.run(sql"select bookId, count(bookId) from BookTag where lower(tag) in (#$tagsParam) and not deleted group by bookId having count(bookId) = ${tags.size}".as[(Int,Int)])    
    idsWithAllTags.map(_.map(_._1) )     
  }
  
  /**
   * Finds the ids of books that have the supplied author
   * @param author The author to match on
   * @return a Future for a Vector[Int] which is the ids of the matching books
   */
  def findBookIdsByAuthor(author:String) = {
    val param = s"%${author.toLowerCase}%"
    db.run(sql"select id from Book where lower(author) like $param and not deleted".as[Int])
  }  
  
  /**
   * Loads the books state from the db
   * @param id The id to load
   * @return a Future for an Option[BookFO]
   */
  def loadEntity(id:Int) = {
    val query = sql"""
      select b.id, b.title, b.author, array_to_string(array_agg(t.tag), ',') as tags, b.cost, b.inventoryAmount, b.createTs, b.modifyTs
      from Book b left join BookTag t on b.id = t.bookId where id = $id and not b.deleted group by b.id   
    """
    db.run(query.as[BookFO].map(_.headOption))
  }
  
  /**
   * Creates a new Book in the system
   * @param book The book to create
   * @return a Future for a Book with the new id assigned
   */
  def persistEntity(book:BookFO) = {
    val insert = 
      sqlu"""
        insert into Book (title, author, cost, inventoryamount, createts) 
        values (${book.title}, ${book.author}, ${book.cost}, ${book.inventoryAmount}, ${book.createTs.toSqlDate })
      """
    val idget = lastIdSelect("book")
    def tagsInserts(bookId:Int) = DBIOAction.sequence(book.tags.map(t => sqlu"insert into BookTag (bookid, tag) values ($bookId, $t)"))
      
    val txn = 
      for{
        bookRes <- insert
        id <- idget
        if id.headOption.isDefined
        _ <- tagsInserts(id.head)
      } yield{
        id.head
      }
          
    db.run(txn.transactionally)    
  }
  
  /**
   * Adds a new tag to a Book
   * @param id The id to update
   * @param tag The tag to add
   * @return a Future representing the number of rows modified
   */
  def tagBook(id:Int, tag:String) = {
    db.run(sqlu"insert into BookTag values ($id, $tag)")  
  }

  /**
   * Removed a tag from a Book
   * @param id The id to update
   * @param tag The tag to remove
   * @return a Future representing the number of rows modified
   */  
  def untagBook(id:Int, tag:String) = {
    db.run(sqlu"delete from BookTag where bookId =  $id and tag = $tag")        
  } 
  
  /**
   * Adds inventory to the book so it can start being sold
   * @param id The id to update
   * @param book The book to add inventory to
   * @param amount The amount to add
   * @@return a Future representing the number of rows modified
   */
  def addInventoryToBook(id:Int, amount:Int) = {
    db.run(sqlu"update Book set inventoryAmount = inventoryAmount + $amount where id = $id")      
  }
  
  /**
   * Removes inventory from a book for an order
   * @param id The id to update
   * @param book The book to remove inventory from
   * @param amount The amount to remove
   * @@return a Future representing the number of rows modified
   */
  def allocateInventory(id:Int, amount:Int) = {
    val fut = 
      db.run(sqlu"update Book set inventoryAmount = inventoryAmount - $amount where id = $id and inventoryAmount >= $amount")
    fut.flatMap{
      case 0 => Future.failed(new InventoryNotAvailableException)
      case amt => Future.successful(amt)
    }
  }  
  
  /**
   * Soft deletes a book from the system
   * @param id The id to update
   * @param book the book to delete
   * @return a Future representing the number of rows modified
   */
  def deleteEntity(id:Int) = {
    val bookDelete = sqlu"update Book set deleted = true where id = $id"
    db.run(bookDelete)
  }
}