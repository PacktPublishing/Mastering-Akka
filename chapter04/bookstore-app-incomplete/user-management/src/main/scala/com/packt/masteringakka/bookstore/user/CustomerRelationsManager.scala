package com.packt.masteringakka.bookstore.user

import com.packt.masteringakka.bookstore.common.BookstoreActor
import com.packt.masteringakka.bookstore.common.EntityActor
import akka.util.Timeout
import akka.actor.Props
import java.util.Date
import com.packt.masteringakka.bookstore.common.EntityAggregate

object CustomerRelationsManager{
  val Name = "crm"
  case class FindUserById(id:Int)
  case class FindUserByEmail(email:String)  
  case class SignupNewUser(input:BookstoreUser.UserInput)
  case class UpdateUserInfo(id:Int, input:BookstoreUser.UserInput)
  case class RemoveUser(userId:Int) 
  
  def props = Props[CustomerRelationsManager]
}

/**
 * Actor class that receives requests for BookstoreUser and delegates to the appropriate entity instance
 */
class CustomerRelationsManager extends EntityAggregate[BookstoreUserFO, BookstoreUser]{
  import com.packt.masteringakka.bookstore.common.EntityActor._
  import CustomerRelationsManager._
  import context.dispatcher
  
  val repo = new BookstoreUserRepository
  
  def receive = {
    case FindUserById(id) =>
      val user = lookupOrCreateChild(id)
      user.forward(GetFieldsObject)
      
    case FindUserByEmail(email) => 
      val result = 
        for{
          id <- repo.findUserIdByEmail(email)
          user = lookupOrCreateChild(id.getOrElse(0))
          vo <- askForFo(user)
        } yield vo
      pipeResponse(result)
        
    case SignupNewUser(input) =>
      val vo = BookstoreUserFO(0, input.firstName, input.lastName, input.email, new Date, new Date)
      persistOperation(vo.id, vo)
      
    case UpdateUserInfo(id, info) =>
      persistOperation(id, BookstoreUser.UpdatePersonalInfo(info))
      
    case RemoveUser(id) =>
      persistOperation(id, Delete)            
  }
  
  def entityProps(id:Int) = BookstoreUser.props(id)
}