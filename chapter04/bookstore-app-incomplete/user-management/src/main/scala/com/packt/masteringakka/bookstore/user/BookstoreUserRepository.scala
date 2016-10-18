package com.packt.masteringakka.bookstore.user

import com.packt.masteringakka.bookstore.common.EntityRepository
import scala.concurrent.ExecutionContext
import slick.jdbc.GetResult

object BookstoreUserRepository{  
  val SelectFields = "select id, firstName, lastName, email, createTs, modifyTs from StoreUser " 
  implicit val GetUser = GetResult{r => BookstoreUserFO(r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp)}  
}

/**
 * Repository class for BookstoreUsers
 */
class BookstoreUserRepository(implicit ec:ExecutionContext) extends EntityRepository[BookstoreUserFO]{
  import BookstoreUserRepository._
  import RepoHelpers._
  import slick.driver.PostgresDriver.api._  
  
  def loadEntity(id:Int) = {
    db.
      run(sql"#$SelectFields where id = $id and not deleted".as[BookstoreUserFO]).
      map(_.headOption)
  }
  def persistEntity(user:BookstoreUserFO) = {
    val insert = sqlu"""
      insert into StoreUser (firstName, lastName, email, createTs, modifyTs) 
      values (${user.firstName}, ${user.lastName}, ${user.email}, ${user.createTs.toSqlDate}, ${user.modifyTs.toSqlDate})
    """
    val idget = lastIdSelect("storeuser")    
    db.run(insert.andThen(idget).withPinnedSession).map(id => id.head)    
  }
 
  def deleteEntity(id:Int) = {
    val emailSuffix = s".$id.deleted"
    db.run(sqlu"update StoreUser set deleted = true, email = email || $emailSuffix where id = $id")
  }
  
  
  /**
   * Finds a user by its email
   * @param email The email to find a user for
   * @return a Future wrapping an Option[BookstoreUser]
   */  
  def findUserIdByEmail(email:String) = {
    db.
      run(sql"select id from StoreUser where email = $email and not deleted".as[Int]).
      map(_.headOption)
  }  
  
  /**
   * Updates firstName, lastName and email for a user
   * @param user The user to update
   * @return a Future for a Int representing the number of updated records
   */
  def updateUserInfo(user:BookstoreUserFO) = {
    val update = sqlu"""
      update StoreUser set firstName = ${user.firstName}, 
      lastName = ${user.lastName}, email = ${user.email} where id = ${user.id}  
    """
    db.run(update)
  } 
}