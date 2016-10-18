package com.packt.masteringakka.bookstore.common

import akka.actor._
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings

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
  def bootup(system:ActorSystem):List[BookstoreRoutesDefinition]
  
  /**
   * Starts up an actor as a singleton
   * @param system The system to use for starting the singleton
   * @param props The props for the singleton actor
   * @param name The name to apply to the manager that manages the singleton
   * @param terminationMessage The termination message to use for that singleton, defaulting to PoisonPill
   * @return an ActorRef to the manager actor for that singleton
   */
  def startSingleton(system:ActorSystem, props:Props, 
    managerName:String, terminationMessage:Any = PoisonPill):ActorRef = {
    
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = props, 
        terminationMessage = terminationMessage, 
        settings = ClusterSingletonManagerSettings(system)),
      managerName)        
  }
}