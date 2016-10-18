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

trait CreditJsonProtocol extends BookstoreJsonProtocol{
  import CreditAssociate._
  implicit val chargeReqFormat = jsonFormat5(ChargeRequest)
  implicit val chargeRespFormat = jsonFormat1(ChargeResponse)   
}

/**
 * Companion to the CreditAssociate actor
 */
object CreditAssociate{
  val Name = "credit-associate"
  def props = Props[CreditAssociate]
  case class ChargeCreditCard(cardInfo:CreditCardInfo, amount:Double)
      
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
  import spray.json._
  implicit val mater = ActorMaterializer()
  
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
  def chargeCard(info:CreditCardInfo, amount:Double):Future[ChargeResponse] = { 
    val chargeReq = ChargeRequest(info.cardHolder, info.cardType, info.cardNumber, info.expiration, amount)
    val entity = HttpEntity(ContentTypes.`application/json`, chargeReq.toJson.prettyPrint )
    val request = HttpRequest(HttpMethods.POST, settings.creditChargeUrl, entity = entity)
    Http(context.system).
      singleRequest(request).
      flatMap{
        case resp if resp.status.isSuccess =>
          Unmarshal(resp.entity).to[ChargeResponse]
        case resp =>
          Future.failed(new RuntimeException(s"Invalid status code received on request: ${resp.status}"))
      }
  }   
}
