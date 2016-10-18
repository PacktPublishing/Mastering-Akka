package com.packt.masteringakka.bookstore.user

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.packt.masteringakka.bookstore.common.BookstoreJsonProtocol
import BookstoreUserViewBuilder._
import CustomerRelationsManager._
import BookstoreUser._

trait UserJsonProtocol extends BookstoreJsonProtocol{
  implicit val bookstoreUserFoFormat = jsonFormat5(BookstoreUserFO.apply)
  implicit val bookstoreUserRmFormat = jsonFormat5(BookstoreUserRM.apply)
  implicit val userInputFormat = jsonFormat2(UserInput)
  implicit val createUserInputFormat = jsonFormat3(CreateUserInput)
}