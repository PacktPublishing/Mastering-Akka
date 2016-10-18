package com.packt.masteringakka.bookstore.credit

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.packt.masteringakka.bookstore.common.BookstoreJsonProtocol
import CreditAssociate._
import spray.json._


trait CreditJsonProtocol extends BookstoreJsonProtocol{
  implicit object CreditTransactionStatusFormatter extends RootJsonFormat[CreditTransactionStatus.Value]{
    def write(status:CreditTransactionStatus.Value) = {
      JsString(status.toString)
    }
    def read(jv:JsValue) = jv match{
      case JsString(s) => CreditTransactionStatus.withName(s)
      case other => throw new DeserializationException(s"expected JsString but got $other")
    }
  }  
  implicit val cardInfoFormat = jsonFormat4(CreditCardInfo)
  implicit val chargeRequestFormat = jsonFormat2(ChargeCreditCard)
  implicit val creditTransactionFoFormat = jsonFormat7(CreditCardTransactionFO.apply)
}