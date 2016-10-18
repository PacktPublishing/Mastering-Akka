package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common.Bootstrap
import akka.actor.ActorSystem

class OrderBoot extends Bootstrap {

  def bootup(system:ActorSystem) = {
    import system.dispatcher
    val salesAssociate = system.actorOf(SalesAssociate.props, SalesAssociate.Name)
    val salesOrderView = system.actorOf(SalesOrderView.props, SalesOrderView.Name)
    system.actorOf(SalesOrderViewBuilder.props, SalesOrderViewBuilder.Name )
    List(new SalesOrderRoutes(salesAssociate, salesOrderView))
  }
}