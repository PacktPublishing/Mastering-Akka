package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.Bootstrap
import akka.actor.ActorSystem
import akka.event.Logging

/**
 * Bootup for the inventory management sub domain model
 */
class InventoryBoot extends Bootstrap{

  def bootup(system:ActorSystem) = {
    import system.dispatcher    
    val inventoryClerk = system.actorOf(InventoryClerk.props, InventoryClerk.Name)
    val bookView = system.actorOf(BookView.props, BookView.Name)
    startSingleton(system, BookViewBuilder.props, BookViewBuilder.Name)
    startSingleton(system, InventoryAllocationEventListener.props(inventoryClerk), InventoryAllocationEventListener.Name)
    List(new InventoryRoutes(inventoryClerk, bookView))
  }
}