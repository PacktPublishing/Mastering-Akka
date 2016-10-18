package com.packt.masteringakka.bookstore.user

import java.util.Date
import com.packt.masteringakka.bookstore.common.EntityActor
import com.packt.masteringakka.bookstore.common.BookstoreRepository
import scala.concurrent.Future
import akka.actor.Props
import com.packt.masteringakka.bookstore.common.Failure
import com.packt.masteringakka.bookstore.common.FailureType
import com.packt.masteringakka.bookstore.common.ErrorMessage
import com.packt.masteringakka.bookstore.common.EntityFieldsObject

case class BookstoreUserFO(id:Int, firstName:String, lastName:String, 
  email:String, createTs:Date, modifyTs:Date, deleted:Boolean = false) extends EntityFieldsObject[BookstoreUserFO]{
  def assignId(id:Int) = this.copy(id = id)
  def markDeleted = this.copy(deleted = true)
}

object BookstoreUser{
  case class UserInput(firstName:String, lastName:String, email:String)
  case class UpdatePersonalInfo(input:UserInput)
  val EmailNotUniqueError = ErrorMessage("user.email.nonunique", Some("The email supplied for a create or update is not unique"))
  class EmailNotUniqueException extends Exception
  def props(id:Int) = Props(classOf[BookstoreUser], id)
}

class BookstoreUser(idInput:Int) extends EntityActor[BookstoreUserFO](idInput){
  import BookstoreUser._
  import EntityActor._
  import akka.pattern.pipe
  import context.dispatcher
  
  val repo = new BookstoreUserRepository
  val errorMapper:ErrorMapper = {
    case ex:EmailNotUniqueException => Failure(FailureType.Validation, EmailNotUniqueError )
  }  
  
  //Overriding this to add email unique check first
  override def customCreateHandling:StateFunction = {
    case Event(vo:BookstoreUserFO, _) =>
      val checkFut = emailUnique(vo.email)
      checkFut.
        map(b => FinishCreate(vo))
        .to(self, sender())
      stay
  }
  
  def initializedHandling:StateFunction = {
    case Event(UpdatePersonalInfo(input), data:InitializedData[BookstoreUserFO]) =>
      val newFo = data.fo.copy(firstName = input.firstName, lastName = input.lastName, email = input.email)
      val persistFut = 
        for{
          _ <- emailUnique(input.email, Some(data.fo.id)) 
          updated <- repo.updateUserInfo(newFo)
        } yield updated
      requestFoForSender
      persist(data.fo, persistFut, id => newFo)
  }
  
  /**
   * Checks to make sure the email is unique
    *
    * @param email The email to check
   * @param existingId Supplied when the user already exists to avoid matching on the same user
   * when checking for uniqueness
   * @return A Future for an Option[Boolean] which will be failed if the email is not unique
   */
  def emailUnique(email:String, existingId:Option[Int] = None) = {    
    repo.
      findUserIdByEmail(email).
      flatMap{
        case None => Future.successful(true)
        case Some(id) if Some(id) == existingId => Future.successful(true)
        case _ => Future.failed(new EmailNotUniqueException)
      }
  }  
}