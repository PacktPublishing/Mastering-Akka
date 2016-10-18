package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.Bootstrap
import akka.actor.ActorSystem

/**
 * Bootup for the inventory management sub domain model
 */
class InventoryBoot extends Bootstrap{

  def bootup(system:ActorSystem) = {
    import system.dispatcher    
    val inventoryClerk = system.actorOf(InventoryClerk.props, InventoryClerk.Name)
    val bookView = system.actorOf(BookView.props, BookView.Name)
    system.actorOf(BookViewBuilder.props, BookViewBuilder.Name)
    List(new InventoryRoutes(inventoryClerk, bookView))
  }
}