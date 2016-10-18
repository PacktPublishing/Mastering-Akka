package com.packt.masteringakka.bookstore.user

import java.util.Date
import com.packt.masteringakka.bookstore.common._
import scala.concurrent.Future
import akka.actor.Props

case class BookstoreUserFO(email:String, firstName:String, lastName:String, 
  createTs:Date, deleted:Boolean = false) extends EntityFieldsObject[String, BookstoreUserFO]{
  def assignId(id:String) = this.copy(email = id)
  def id = email
  def markDeleted = this.copy(deleted = true)
}
object BookstoreUserFO{
  def empty = BookstoreUserFO("", "", "", new Date(0))
}


object BookstoreUser{
  case class UserInput(firstName:String, lastName:String)
  
  object Command{
    case class CreateUser(user:BookstoreUserFO)
    case class UpdatePersonalInfo(input:UserInput)
  }
  
  object Event{
    trait UserEvent extends EntityEvent{def entityType = "user"}
    case class UserCreated(user:BookstoreUserFO) extends UserEvent{
      def toDatamodel = {
        val userDm = Datamodel.BookstoreUser.newBuilder().
          setEmail(user.email).
          setFirstName(user.firstName ).
          setLastName(user.lastName).
          setCreateTs(user.createTs.getTime).
          setDeleted(user.deleted).
          build
        
        Datamodel.UserCreated.newBuilder().
          setUser(userDm).
          build
      }
    }
    object UserCreated extends DatamodelReader{
      def fromDatamodel = {
        case dm:Datamodel.UserCreated =>
          val user = dm.getUser()
          UserCreated(BookstoreUserFO(user.getEmail(), user.getFirstName(), user.getLastName(), new Date(user.getCreateTs()), user.getDeleted()))
      }
    }
    
    case class PersonalInfoUpdated(firstName:String, lastName:String) extends UserEvent{
      def toDatamodel = Datamodel.PersonalInfoUpdated.newBuilder().
        setFirstName(firstName).
        setLastName(lastName).
        build
    }
    object PersonalInfoUpdated extends DatamodelReader{
      def fromDatamodel = {
        case dm:Datamodel.PersonalInfoUpdated =>
          PersonalInfoUpdated(dm.getFirstName(), dm.getLastName())
      }
    }
    
    case class UserDeleted(email:String) extends UserEvent{
      def toDatamodel = Datamodel.UserDeleted.newBuilder().
        setEmail(email).
        build
    }   
    object UserDeleted extends DatamodelReader{
      def fromDatamodel = {
        case dm:Datamodel.UserDeleted =>
          UserDeleted(dm.getEmail())
      }
    }
    
  }
  
  def props(id:String) = Props(classOf[BookstoreUser], id)
   
}

class BookstoreUser(email:String) extends PersistentEntity[BookstoreUserFO](email){
  import BookstoreUser._
  import Command._
  import Event._
  import akka.pattern.pipe
  import context.dispatcher
  
  def initialState = BookstoreUserFO.empty
  
  def additionalCommandHandling = {
    case CreateUser(user) =>
      persist(UserCreated(user)){handleEventAndRespond()}
      
    case UpdatePersonalInfo(input) =>
      persist(PersonalInfoUpdated(input.firstName, input.lastName)){handleEventAndRespond()}
  }
  
  def handleEvent(event:EntityEvent) = event match {
    case UserCreated(user) =>
      state = user
    case PersonalInfoUpdated(first, last) =>
      state = state.copy(firstName = first, lastName = last)
    case UserDeleted(email) =>
      state = state.markDeleted
  }
  
  def isCreateMessage(cmd:Any) = cmd match{
    case cr:CreateUser => true
    case _ => false
  }  
  
  override def newDeleteEvent = Some(UserDeleted(email))
}