package com.packt.masteringakka.bookstore.user

import akka.actor._
import com.packt.masteringakka.bookstore.common._
import slick.jdbc.GetResult
import java.util.Date
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.packt.masteringakka.bookstore.common.BookStoreActor
import com.packt.masteringakka.bookstore.domain.user.UserInput
import com.packt.masteringakka.bookstore.domain.user.UpdateUserInfo
import com.packt.masteringakka.bookstore.domain.user.FindUserById
import com.packt.masteringakka.bookstore.domain.user.CreateUser
import com.packt.masteringakka.bookstore.domain.user.BookstoreUser
import com.packt.masteringakka.bookstore.domain.user.FindUserByEmail
import com.packt.masteringakka.bookstore.domain.user.DeleteUser

/**
 * Companion to the UserManager service actor
 */
object UserManager{
  val Name = "user-manager"
  def props = Props[UserManager]
  class EmailNotUniqueException extends Exception
  val EmailNotUniqueError = ErrorMessage("user.email.nonunique", Some("The email supplied for a create or update is not unique"))
}

/**
 * Service actor for managing users
 */
class UserManager extends BookStoreActor{
  import akka.pattern.pipe
  import UserManager._
  import context.dispatcher
  
  val dao = new UserManagerDao
  
  //PF that will be used to recover from a non unique email exception to a Failure that reflects that failed validation
  val recoverEmailCheck:PartialFunction[Throwable, ServiceResult[_]] = {
    case ex:EmailNotUniqueException => 
      Failure(FailureType.Validation, EmailNotUniqueError )
  }
  
  def receive = {
    case FindUserById(id) =>
      pipeResponse(dao.findUserById(id))
      
    case FindUserByEmail(email) =>
      pipeResponse(dao.findUserByEmail(email))      
    
    case CreateUser(UserInput(first, last, email)) =>
      val result = 
        for{
          _ <- emailUnique(email)
          daoRes <- dao.createUser(BookstoreUser(0, first, last, email, new Date, new Date))
        } yield daoRes        
      pipeResponse(result.recover(recoverEmailCheck ))
      
    case upd @ UpdateUserInfo(id, input) =>
      val result = 
        for{
          _ <- emailUnique(input.email, Some(id))
          userOpt <- dao.findUserById(id)
          updated <- maybeUpdate(upd, userOpt)
        } yield updated
      pipeResponse(result.recover(recoverEmailCheck ))
      
    case DeleteUser(userId) =>
      val result = 
        for{
          userOpt <- dao.findUserById(userId)
          res <- userOpt.fold[Future[Option[BookstoreUser]]](Future.successful(None)){u => 
            dao.deleteUser(u).map(Some.apply)
          }
        } yield res
      pipeResponse(result)
      
  }
  
  /**
   * Checks to make sure the email is unique
   * @param email The email to check
   * @param existingId Supplied when the user already exists to avoid matching on the same user
   * when checking for uniqueness
   * @return A Future for an Option[Boolean] which will be failed if the email is not unique
   */
  def emailUnique(email:String, existingId:Option[Int] = None) = {
    dao.
      findUserByEmail(email).
      flatMap{
        case None => Future.successful(true)
        case Some(user) if Some(user.id) == existingId => Future.successful(true)
        case _ => Future.failed(new EmailNotUniqueException)
      }
  }
  
  /**
   * Will perform an update to the user if it exists
   * @param upd The info to update onto the user if it exists
   * @param userOpt The lookup result for the user.  If Some, it will be updated
   * @return A Future for an Option[BookstoreUser]
   */
  def maybeUpdate(upd:UpdateUserInfo, userOpt:Option[BookstoreUser]) = 
    userOpt.
      map{ u =>
        val updated = u.copy(firstName = upd.input.firstName, lastName = upd.input.lastName, email = upd.input.email)
        dao.updateUserInfo(updated).map(Some.apply)
      }.
      getOrElse(Future.successful(None))
      
}

/**
 * Companion object for the UserManagerDao
 */
object UserManagerDao{
  val SelectFields = "select id, firstName, lastName, email, createTs, modifyTs from StoreUser " 
  implicit val GetUser = GetResult{r => BookstoreUser(r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp)}
}

/**
 * Dao class for interacting with Postgres to perform user related actions
 */
class UserManagerDao(implicit ec:ExecutionContext) extends BookstoreDao{
  import DaoHelpers._
  import UserManagerDao._
  import slick.driver.PostgresDriver.api._
  
  /**
   * Creates a new user
   * @param user The user to create
   * @return a Future wrapping a Bookstore user with the id assigned
   */
  def createUser(user:BookstoreUser) = {
    val insert = sqlu"""
      insert into StoreUser (firstName, lastName, email, createTs, modifyTs) 
      values (${user.firstName}, ${user.lastName}, ${user.email}, ${user.createTs.toSqlDate}, ${user.modifyTs.toSqlDate})
    """
    val idget = lastIdSelect("storeuser")    
    db.run(insert.andThen(idget).withPinnedSession).map(id => user.copy(id = id.headOption.getOrElse(0)))
  }
  
  /**
   * Finds a user by its id
   * @param id The id to find the user for
   * @return a Future wrapping an Option[BookstoreUser]
   */
  def findUserById(id:Int) = {
    db.
      run(sql"#$SelectFields where id = $id and not deleted".as[BookstoreUser]).
      map(_.headOption)
  }
  
  /**
   * Finds a user by its email
   * @param email The email to find a user for
   * @return a Future wrapping an Option[BookstoreUser]
   */  
  def findUserByEmail(email:String) = {
    db.
      run(sql"#$SelectFields where email = $email and not deleted".as[BookstoreUser]).
      map(_.headOption)
  }  
  
  /**
   * Updates firstName, lastName and email for a user
   * @param user The user to update
   * @return a Future for a BookstoreUser
   */
  def updateUserInfo(user:BookstoreUser) = {
    val update = sqlu"""
      update StoreUser set firstName = ${user.firstName}, 
      lastName = ${user.lastName}, email = ${user.email} where id = ${user.id}  
    """
    db.run(update).map(_ => user)
  }
  
  /**
   * Deletes a user from the database
   * @param user The user to delete
   * @return A Future wrapping the user that was deleted
   */
  def deleteUser(user:BookstoreUser) = {
    db.run(sqlu"delete from StoreUser where id = ${user.id}").map(_ => user.copy(deleted = true))
  }
}
