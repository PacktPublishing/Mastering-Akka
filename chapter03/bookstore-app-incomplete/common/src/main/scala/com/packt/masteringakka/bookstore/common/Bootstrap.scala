package com.packt.masteringakka.bookstore.common

import akka.actor.ActorSystem

/**
 * Trait that defines a class that will boot up actors from within a specific services module
 */
trait Bootstrap{
  
  /**
   * Books up the actors for a service module and returns the service endpoints for that
   * module to be included in the Unfiltered server as plans
   * @param system The actor system to boot actors into
   * @return a List of BookstorePlans to add as plans into the server
   */
  def bootup(system:ActorSystem):List[BookstorePlan]
}