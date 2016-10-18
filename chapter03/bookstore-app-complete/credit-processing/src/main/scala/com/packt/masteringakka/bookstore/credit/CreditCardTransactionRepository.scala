package com.packt.masteringakka.bookstore.credit

import com.packt.masteringakka.bookstore.common.EntityRepository
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import slick.jdbc.GetResult

/**
 * Companion to the CreditCardTransactionRepository class
 */
object CreditCardTransactionRepository{
  implicit val GetTxn = GetResult{r => CreditCardTransactionFO(r.<<, CreditCardInfo(r.<<, r.<<, r.<<, r.nextDate), r.<<, CreditTransactionStatus.withName(r.<<), r.<<, r.nextTimestamp, r.nextTimestamp, false)}
}

/**
 * Repository class for dealing with CreditCardTransaction entities
 */
class CreditCardTransactionRepository(implicit ec:ExecutionContext) extends EntityRepository[CreditCardTransactionFO]{
  import RepoHelpers._
  import CreditCardTransactionRepository._
  import slick.driver.PostgresDriver.api._
  import slick.dbio.DBIOAction  
  
  def loadEntity(id:Int) = {
    val select = sql"select id, cardHolder, cardType, cardNumber, expiration, amount, status, confirmationCode, createTs, modifyTs from CreditCardTransaction where id = $id"
    db.run(select.as[CreditCardTransactionFO]).map(_.headOption)
  }
  
  def persistEntity(fo:CreditCardTransactionFO) = {
    val info = fo.cardInfo 
    val insert = sqlu"""
      insert into CreditCardTransaction (cardHolder, cardType, cardNumber, expiration, amount, status, confirmationCode, createTs, modifyTs) 
      values (${info.cardHolder}, ${info.cardType}, ${info.cardNumber}, ${info.expiration.toSqlDate}, ${fo.amount}, ${fo.status.toString}, ${fo.confirmationCode}, ${fo.createTs.toSqlDate}, ${fo.modifyTs.toSqlDate})
    """
    val getId = lastIdSelect("creditcardtransaction")
    db.run(insert.andThen(getId).withPinnedSession).map(_.head)  
  }
  
  //Not implemented
  def deleteEntity(id:Int) = Future.failed(new NotImplementedError("delete not implemented for credit card transaction"))
}