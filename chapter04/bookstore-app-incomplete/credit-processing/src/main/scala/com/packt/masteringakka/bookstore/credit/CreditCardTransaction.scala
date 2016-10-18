package com.packt.masteringakka.bookstore.credit

import akka.actor.Props
import com.packt.masteringakka.bookstore.common.EntityActor
import java.util.Date
import dispatch._
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import com.packt.masteringakka.bookstore.common.EntityFieldsObject

object CreditTransactionStatus extends Enumeration{
  val Approved, Rejected = Value
}
case class CreditCardInfo(cardHolder:String, cardType:String, cardNumber:String, expiration:Date)
case class CreditCardTransactionFO(id:Int, cardInfo:CreditCardInfo, amount:Double, 
  status:CreditTransactionStatus.Value, confirmationCode:Option[String], createTs:Date, modifyTs:Date, deleted:Boolean = false) extends EntityFieldsObject[Int, CreditCardTransactionFO]{
  def assignId(id:Int) = {
    this.copy(id = id)
  }
  def markDeleted = this.copy(deleted = true)
}

/**
 * Companion to the CreditCardTransaction entity
 */
object CreditCardTransaction{
  def props(id:Int) = Props(classOf[CreditCardTransaction], id)
  
  implicit val formats = Serialization.formats(NoTypeHints)     
  case class ChargeRequest(cardHolder:String, cardType:String, cardNumber:String, expiration:Date, amount:Double)
  case class ChargeResponse(confirmationCode:String)  
}

/**
 * Entity class representing a credit transaction in the bookstore app
 */
class CreditCardTransaction(idInput:Int) extends EntityActor[CreditCardTransactionFO](idInput){
  import CreditCardTransaction._
  import context.dispatcher
  import akka.pattern.pipe
  
  val settings = CreditSettings(context.system)
  val errorMapper = PartialFunction.empty
  val repo = new CreditCardTransactionRepository
  
  override def customCreateHandling:StateFunction = {
    case Event(fo:CreditCardTransactionFO, _) =>
      chargeCard(fo.cardInfo, fo.amount ).
        map(resp => FinishCreate(fo.copy(confirmationCode = Some(resp.confirmationCode)))).
        to(self, sender())      
      stay
  }
  
  def initializedHandling = PartialFunction.empty
  
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