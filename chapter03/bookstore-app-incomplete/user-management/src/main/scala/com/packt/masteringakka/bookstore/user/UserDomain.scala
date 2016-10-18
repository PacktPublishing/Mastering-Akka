package com.packt.masteringakka.bookstore.user

import java.util.Date

//Persistent entities
case class BookstoreUser(id:Int, firstName:String, lastName:String, email:String, createTs:Date, modifyTs:Date, deleted:Boolean = false)

//Lookup Operations
case class FindUserById(id:Int)
case class FindUserByEmail(email:String)

//Modify operations
case class UserInput(firstName:String, lastName:String, email:String)
case class CreateUser(input:UserInput)
case class UpdateUserInfo(id:Int, input:UserInput)
case class DeleteUser(userId:Int)