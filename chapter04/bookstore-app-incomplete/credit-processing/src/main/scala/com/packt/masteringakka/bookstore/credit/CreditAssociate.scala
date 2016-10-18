package com.packt.masteringakka.bookstore.credit

import akka.actor._
import scala.concurrent.ExecutionContext
import java.util.Date
import com.packt.masteringakka.bookstore.common.BookstoreActor
import com.packt.masteringakka.bookstore.common.BookstoreRepository
import com.packt.masteringakka.bookstore.common.EntityAggregate

/**
 * Companion to the CreditAssociate actor
 */
object CreditAssociate{
  val Name = "credit-associate"
  def props = Props[CreditAssociate]
  case class ChargeCreditCard(cardInfo:CreditCardInfo, amount:Double)
}

/**
 * Factory type actor for creating and finding CreditCardTransaction entities
 */
class CreditAssociate extends EntityAggregate[CreditCardTransactionFO, CreditCardTransaction]{
  import akka.pattern.pipe
  import context.dispatcher  
  import CreditAssociate._
  
  val repo = new CreditCardTransactionRepository
  val settings = CreditSettings(context.system)
  
  def receive = {
    case ChargeCreditCard(info, amount) => 
      val  txn = lookupOrCreateChild(0)
      val fo = CreditCardTransactionFO(0, info, amount, CreditTransactionStatus.Approved, None, new Date, new Date)
      txn.forward(fo)
  }
  
  def entityProps(id:Int) = CreditCardTransaction.props(id)
}
