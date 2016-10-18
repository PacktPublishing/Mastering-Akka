package com.packt.masteringakka.bookstore.common

import io.netty.channel.ChannelHandler.Sharable
import unfiltered.netty.ServerErrorResponse
import unfiltered.request._
import java.util.Date
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import unfiltered.response._

/**
 * This endpoint is meant to simulate an some external credit card charging service like Square
 */
@Sharable
object PretentCreditCardService extends unfiltered.netty.async.Plan with ServerErrorResponse{
  case class ChargeRequest(cardHolder:String, cardType:String, cardNumber:String, expiration:Date, amount:Double)
  case class ChargeResponse(confirmationCode:String)
  implicit val formats = Serialization.formats(NoTypeHints)
  
  def intent = {
    case req @ POST(Path(Seg("credit" :: "charge" :: Nil))) =>
      val body = Body.string(req)
      val request = read[ChargeRequest](body)
      val resp = write(ChargeResponse(java.util.UUID.randomUUID().toString))
      req.respond(JsonContent ~> ResponseString(resp))
  }
}