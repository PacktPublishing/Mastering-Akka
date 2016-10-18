package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common.Bootstrap
import akka.actor.ActorSystem

class OrderBoot extends Bootstrap {

  def bootup(system:ActorSystem) = {
    import system.dispatcher
    val salesAssociate = system.actorOf(SalesAssociate.props, SalesAssociate.Name)
    List(new SalesOrderEndpoint(salesAssociate))
  }
}