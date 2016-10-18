package com.packt.masteringakka.bookstore.credit

import akka.actor._
import scala.concurrent.ExecutionContext
import java.util.Date
import com.packt.masteringakka.bookstore.common._
import java.util.UUID
import scala.concurrent.Future
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.unmarshalling.Unmarshal


/**
 * Companion to the CreditAssociate actor
 */
object CreditAssociate{
  val Name = "credit-associate"
  def props = Props[CreditAssociate]
  case class ChargeCreditCard(cardInfo:CreditCardInfo, amount:Double)
  case class FindTransactionById(id:String)
      
  case class ChargeRequest(cardHolder:String, cardType:String, cardNumber:String, expiration:Date, amount:Double)
  case class ChargeResponse(confirmationCode:String)    
}

/**
 * Aggregate type actor for creating and finding CreditCardTransaction entities
 */
class CreditAssociate extends Aggregate[CreditCardTransactionFO, CreditCardTransaction] with CreditJsonProtocol{
  import akka.pattern.pipe
  import context.dispatcher  
  import CreditAssociate._
  import PersistentEntity._
  import spray.json._
  implicit val mater = ActorMaterializer()
  
  def receive = {
    case FindTransactionById(id) => 
      forwardCommand(id, GetState(id))    
    
    case ChargeCreditCard(info, amount) =>
      val caller = sender()
      chargeCard(info, amount ).onComplete{
        case util.Success(result) =>
          val id = UUID.randomUUID().toString
          val fo = CreditCardTransactionFO(id, info, amount, CreditTransactionStatus.Approved, Some(result.confirmationCode), new Date)
          val command = CreditCardTransaction.Command.CreateCreditTransaction(fo)
          entityShardRegion.tell(command, caller)
          
        case util.Failure(ex) =>
          log.error(ex, "Error performing credit charging")
          caller ! Failure(FailureType.Service, ServiceResult.UnexpectedFailure)
      }
  }
  
  def entityProps = CreditCardTransaction.props
  
  /**
   * Calls the external service to charge the credit card
   * @param info The card info to charge
   * @amount The amount to charge
   * @return a Future wrapping the response from the charge request
   */
  def chargeCard(info:CreditCardInfo, amount:Double):Future[ChargeResponse] = { 
    //No more need to fake this out with an actual call, just stub back as a successful Future
    val id = UUID.randomUUID().toString
    Future.successful(ChargeResponse(id))
  }   
}
