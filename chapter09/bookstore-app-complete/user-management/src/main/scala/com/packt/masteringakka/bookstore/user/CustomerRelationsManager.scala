package com.packt.masteringakka.bookstore.user

import com.packt.masteringakka.bookstore.common._
import akka.util.Timeout
import akka.actor.Props
import java.util.Date

object CustomerRelationsManager{
  val Name = "crm"
  case class CreateUserInput(email:String, firstName:String, lastName:String)
  case class FindUserByEmail(email:String)  
  case class SignupNewUser(input:CreateUserInput)
  case class UpdateUserInfo(email:String, input:BookstoreUser.UserInput)
  case class RemoveUser(email:String) 
  
  def props = Props[CustomerRelationsManager]
  
  val EmailNotUniqueError = ErrorMessage("user.email.nonunique", Some("The email supplied for a create or update is not unique"))  
}

/**
 * Aggregate actor class that receives requests for BookstoreUser and delegates to the appropriate entity instance
 */
class CustomerRelationsManager extends Aggregate[BookstoreUserFO, BookstoreUser]{
  import com.packt.masteringakka.bookstore.common.PersistentEntity._
  import CustomerRelationsManager._
  import BookstoreUser._
  import Command._
  import context.dispatcher
  import akka.pattern.ask
  import concurrent.duration._
  
  def receive = {
      
    case FindUserByEmail(email) => 
      forwardCommand(email, GetState(email))
        
    case SignupNewUser(input) =>
      //Check uniqueness of email here
      implicit val timeout = Timeout(5 seconds)
      val stateFut = (entityShardRegion ? GetState(input.email)).mapTo[ServiceResult[BookstoreUserFO]]
      val caller = sender()
      stateFut onComplete{
        case util.Success(FullResult(user)) =>
          caller ! Failure(FailureType.Validation, EmailNotUniqueError)
          
        case util.Success(EmptyResult) =>
          val fo = BookstoreUserFO(input.email, input.firstName, input.lastName, new Date)
          entityShardRegion.tell(CreateUser(fo), caller)
          
        case _ =>
          caller ! Failure(FailureType.Service, ServiceResult.UnexpectedFailure)
      }

      
    case UpdateUserInfo(email, info) =>
      forwardCommand(email, UpdatePersonalInfo(info, email))
      
    case RemoveUser(email) =>
      forwardCommand(email, MarkAsDeleted)            
  }
  
  def entityProps = BookstoreUser.props
}