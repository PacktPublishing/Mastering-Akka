package com.packt.masteringakka.bookstore.common

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import java.util.Date

/**
 * Root json protocol class for others to extend from
 */
trait BookstoreJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol{
  implicit object DateFormat extends JsonFormat[Date] {
    def write(date : Date) : JsValue = JsNumber(date.getTime)
    def read(json: JsValue) : Date = json match {
      case JsNumber(epoch) => new Date(epoch.toLong)
      case unknown => deserializationError(s"Expected JsString, got $unknown")
    }
  }
  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any) = x match {
      case n: Int => JsNumber(n)
      case s: String => JsString(s)
      case b: Boolean if b == true => JsTrue
      case b: Boolean if b == false => JsFalse
    }
    def read(value: JsValue) = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case JsTrue => true
      case JsFalse => false
    }
  }  
}