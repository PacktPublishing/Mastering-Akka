package com.packt.masteringakka.bookstore.server

import java.util.Date
import com.packt.masteringakka.bookstore.common.BookstoreRoutesDefinition
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.packt.masteringakka.bookstore.common.BookstoreJsonProtocol
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import akka.stream.Materializer
import akka.http.scaladsl.server.Route

/**
 * This route is meant to simulate an some external credit card charging service like Square
 */
object PretendCreditCardService extends BookstoreRoutesDefinition with BookstoreJsonProtocol{
  import akka.http.scaladsl.server.Directives._
  
  case class ChargeRequest(cardHolder:String, cardType:String, cardNumber:String, expiration:Date, amount:Double)
  case class ChargeResponse(confirmationCode:String)
  implicit val chargeReqFormat = jsonFormat5(ChargeRequest)
  implicit val chargeRespFormat = jsonFormat1(ChargeResponse)
  
  def routes(implicit system:ActorSystem, ec:ExecutionContext, mater:Materializer):Route = {
    path("credit" / "charge"){
      post{
        entity(as[ChargeRequest]){ req =>
          complete(ChargeResponse(java.util.UUID.randomUUID().toString))
        }
      }
    }
  }
}