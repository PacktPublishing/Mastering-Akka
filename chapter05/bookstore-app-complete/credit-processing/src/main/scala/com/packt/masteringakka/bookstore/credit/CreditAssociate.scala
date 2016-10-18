package com.packt.masteringakka.bookstore.credit

import akka.actor._
import scala.concurrent.ExecutionContext
import java.util.Date
import com.packt.masteringakka.bookstore.common._
import dispatch._
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import java.util.UUID

/**
 * Companion to the CreditAssociate actor
 */
object CreditAssociate{
  val Name = "credit-associate"
  def props = Props[CreditAssociate]
  case class ChargeCreditCard(cardInfo:CreditCardInfo, amount:Double)
  
  implicit val formats = Serialization.formats(NoTypeHints)     
  case class ChargeRequest(cardHolder:String, cardType:String, cardNumber:String, expiration:Date, amount:Double)
  case class ChargeResponse(confirmationCode:String)   
}

/**
 * Aggregate type actor for creating and finding CreditCardTransaction entities
 */
class CreditAssociate extends Aggregate[CreditCardTransactionFO, CreditCardTransaction]{
  import akka.pattern.pipe
  import context.dispatcher  
  import CreditAssociate._
  
  val settings = CreditSettings(context.system)
  
  def receive = {
    case ChargeCreditCard(info, amount) =>
      val caller = sender()
      chargeCard(info, amount ).onComplete{
        case util.Success(result) =>
          val id = UUID.randomUUID().toString
          val fo = CreditCardTransactionFO(id, info, amount, CreditTransactionStatus.Approved, Some(result.confirmationCode), new Date)
          val txn = lookupOrCreateChild(id)
          txn.tell(CreditCardTransaction.Command.CreateCreditTransaction(fo), caller)
          
        case util.Failure(ex) =>
          log.error(ex, "Error performing credit charging")
          caller ! Failure(FailureType.Service, ServiceResult.UnexpectedFailure)
      }
  }
  
  def entityProps(id:String) = CreditCardTransaction.props(id)
  
  /**
   * Calls the external service to charge the credit card
   * @param info The card info to charge
   * @amount The amount to charge
   * @return a Future wrapping the response from the charge request
   */
  def chargeCard(info:CreditCardInfo, amount:Double) = { 
    val jsonBody = write(ChargeRequest(info.cardHolder, info.cardType, info.cardNumber, info.expiration, amount))
    val request = url(settings.creditChargeUrl) << jsonBody
    Http(request OK as.String).map(read[ChargeResponse])
  }   
}
