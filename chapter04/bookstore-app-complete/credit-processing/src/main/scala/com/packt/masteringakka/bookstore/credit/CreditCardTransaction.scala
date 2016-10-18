package com.packt.masteringakka.bookstore.credit

import akka.actor.Props
import com.packt.masteringakka.bookstore.common._
import java.util.Date

object CreditTransactionStatus extends Enumeration{
  val Approved, Rejected = Value
}
case class CreditCardInfo(cardHolder:String, cardType:String, cardNumber:String, expiration:Date)
case class CreditCardTransactionFO(id:String, cardInfo:CreditCardInfo, amount:Double, 
  status:CreditTransactionStatus.Value, confirmationCode:Option[String], createTs:Date, deleted:Boolean = false) extends EntityFieldsObject[String, CreditCardTransactionFO]{
  def assignId(id:String) = {
    this.copy(id = id)
  }
  def markDeleted = this.copy(deleted = true)
}
object CreditCardTransactionFO{
  def empty = CreditCardTransactionFO("", CreditCardInfo("", "", "", new Date(0)), 0, CreditTransactionStatus.Rejected, None, new Date(0))
}


/**
 * Companion to the CreditCardTransaction entity
 */
object CreditCardTransaction{
  def props(id:String) = Props(classOf[CreditCardTransaction], id) 
  
  object Command{
    case class CreateCreditTransaction(txn:CreditCardTransactionFO)
  }
  
  object Event{
    case class CreditTransactionCreated(txn:CreditCardTransactionFO) extends EntityEvent{
      def toDatamodel = {
        val cardInfoDm = Datamodel.CreditCardInfo.newBuilder().
          setCardHolder(txn.cardInfo.cardHolder ).
          setCardNumber(txn.cardInfo.cardNumber).
          setCardType(txn.cardInfo.cardType ).
          setExpiration(txn.cardInfo.expiration.getTime).
          build
        
        val builder = Datamodel.CreditCardTransaction.newBuilder().
          setAmount(txn.amount ).
          setCardInfo(cardInfoDm).
          setCreateTs(txn.createTs.getTime).
          setId(txn.id).
          setStatus(txn.status.toString)
          
        txn.confirmationCode.foreach(c => builder.setConfirmationCode(c))
        Datamodel.CreditTransactionCreated.newBuilder().
          setTxn(builder.build).
          build
      }
    }
    object CreditTransactionCreated extends DatamodelReader{
      def fromDatamodel = {
        case dm:Datamodel.CreditTransactionCreated =>
          val txnDm = dm.getTxn()
          val cardInfoDm = txnDm.getCardInfo()
          val conf = 
            if (txnDm.hasConfirmationCode()) Some(txnDm.getConfirmationCode())
            else None
          
          CreditCardTransactionFO(txnDm.getId(), CreditCardInfo(cardInfoDm.getCardHolder(), cardInfoDm.getCardType(),
            cardInfoDm.getCardNumber(), new Date(cardInfoDm.getExpiration())), txnDm.getAmount(), 
            CreditTransactionStatus.withName(txnDm.getStatus()), conf, new Date(txnDm.getCreateTs()))
      }
    }
  }
}

/**
 * Entity class representing a credit transaction in the bookstore app
 */
class CreditCardTransaction(id:String) extends PersistentEntity[CreditCardTransactionFO](id){
  import CreditCardTransaction._
  import Command._
  import Event._
  import context.dispatcher
  import akka.pattern.pipe
  
  val settings = CreditSettings(context.system)
  
  def initialState = CreditCardTransactionFO.empty
  
  def additionalCommandHandling = {
    case CreateCreditTransaction(txn) =>
      persist(CreditTransactionCreated(txn)){handleEventAndRespond()}
  }
  
  def handleEvent(event:EntityEvent) = event match {
    case CreditTransactionCreated(txn) =>
      state = txn
  }
  
  def isCreateMessage(cmd:Any) = cmd match{
    case cr:CreateCreditTransaction => true
    case _ => false
  }

}