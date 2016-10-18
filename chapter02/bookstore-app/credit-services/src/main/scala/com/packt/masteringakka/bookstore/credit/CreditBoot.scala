package com.packt.masteringakka.bookstore.credit

import com.packt.masteringakka.bookstore.common.Bootstrap
import akka.actor.ActorSystem

/**
 * Boot class for the credit sub domain of the app
 */
class CreditBoot extends Bootstrap{
  def bootup(system:ActorSystem) = {
    system.actorOf(CreditCardTransactionHandler.props, CreditCardTransactionHandler.Name)
    Nil
  }
}