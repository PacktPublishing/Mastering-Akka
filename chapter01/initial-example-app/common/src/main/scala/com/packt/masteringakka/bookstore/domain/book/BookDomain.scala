package com.packt.masteringakka.bookstore.domain.book

import java.util.Date

//Persistent entities
case class Book(id:Int, title:String, author:String, tags:List[String], cost:Double, inventoryAmount:Int, createTs:Date, modifyTs:Date, deleted:Boolean = false)

//Lookup operations
case class FindBook(id:Int)
case class FindBooksForIds(ids:Seq[Int])
case class FindBooksByTags(tags:Seq[String])
case class FindBooksByTitle(title:String)
case class FindBooksByAuthor(author:String)

//Modify operations
case class CreateBook(title:String, author:String, tags:List[String], cost:Double)
case class AddTagToBook(bookId:Int, tag:String)
case class RemoveTagFromBook(bookId:Int, tag:String)
case class AddInventoryToBook(bookId:Int, amount:Int)
case class DeleteBook(id:Int)