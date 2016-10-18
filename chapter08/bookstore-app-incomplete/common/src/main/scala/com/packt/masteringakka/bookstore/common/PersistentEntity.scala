package com.packt.masteringakka.bookstore.common

import akka.persistence.PersistentActor
import akka.actor.ReceiveTimeout
import akka.actor.ActorLogging
import scala.reflect.ClassTag
import akka.actor.Props
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.SaveSnapshotFailure
import akka.persistence.SnapshotOffer
import akka.persistence.RecoveryCompleted
import akka.cluster.sharding.ShardRegion.HashCodeMessageExtractor
import akka.actor.ActorSystem
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardRegion

/**
 * Base trait for all entity based commands to extend from
 */
trait EntityCommand{
  
  /**
   * Gets the id of the entity that this command is for, to
   * use for shard routing
   * @return a String representing the entity id of this command
   */
  def entityId:String
}

/**
 * Marker trait for something that is an event generated as the result of a command
 */
trait EntityEvent extends Serializable with DatamodelWriter{
  /**
   * Gets the string identifier of the entity this event is for, for tagging purposes
   */
  def entityType:String
}

/**
 * Companion to the PersistentEntity abstract class
 */
object PersistentEntity{
  
  /**
   * Send back to the entity by the parent Shard to finish the passivation process
   */
  case object StopEntity
  
  /** Request to get the current state from an entity actor */
  case class GetState(id:String) extends EntityCommand{
    def entityId = id
  }
  
  /** Request to mark an entity instance as deleted*/
  case class MarkAsDeleted(id:String)  extends EntityCommand{
    def entityId = id
  }
  
  /**
   * Class that will be instantntiated on a per entity bases to represent
   * the different shard related extraction functions.  This is based on the Java APIs
   * HashCodeMessageExtractor but uses a more scala friendly approach
   */
  class PersistentEntityIdExtractor(maxShards:Int) {
    val extractEntityId:ShardRegion.ExtractEntityId = {
      case ec:EntityCommand => (ec.entityId, ec)      
    }
    
    val extractShardId:ShardRegion.ExtractShardId = {
      case ec:EntityCommand => 
        (math.abs(ec.entityId.hashCode) % maxShards).toString
    }
  }
  
  /**
   * Companion to the PersistentEntityIdExtractor
   */
  object PersistentEntityIdExtractor{
    
    /**
     * Creates a new instance of PersistentEntityIdExtractor given
     * an actor system where the max shards config can be pulled from
     * @param system The system where the config will be read from for max shards
     */
    def apply(system:ActorSystem):PersistentEntityIdExtractor =  {
      val maxShards = system.settings.config.getInt("maxShards")
      new PersistentEntityIdExtractor(maxShards)
    }
  }
}

/**
 * Base class for the Event Sourced entities to extend from
 */
abstract class PersistentEntity[FO <: EntityFieldsObject[String, FO]: ClassTag]
  extends PersistentActor with ActorLogging{
  import PersistentEntity._
  import ShardRegion.Passivate
  import concurrent.duration._
    
  val id = self.path.name
  val entityType = getClass.getSimpleName
  var state:FO = initialState
  var eventsSinceLastSnapshot = 0
  
  //Using this scheduled task as the passivation mechanism
  context.setReceiveTimeout(1 minute)  
  
  //Dynamically setting the persistence id as a combo of 
  //entity type and the id of the entity instance
  override def persistenceId = s"$entityType-$id"
  
  //Recovery combines the standard handling plus the custom handling
  def receiveRecover = standardRecover orElse customRecover
  
  /**
   * Standard entity recovery logic that all entities will have
   */
  def standardRecover:Receive = {
    
    //For any entity event, just call handleEvent
    case ev:EntityEvent =>       
      log.info("Recovering persisted event: {}", ev)
      handleEvent(ev)
      eventsSinceLastSnapshot += 1
      
    case SnapshotOffer(meta, snapshot:FO) =>
      log.info("Recovering entity with a snapshot: {}", snapshot)
      state = snapshot
      
    case RecoveryCompleted =>
      log.debug("Recovery completed for {} entity with id {}", entityType , id)
  }
  
  /**
   * Optional custom recovery message handling that a subclass can provide if necessary
   */
  def customRecover:Receive = PartialFunction.empty
  
  //Command handling combines standard handling plus custom handling
  def receiveCommand = standardCommandHandling orElse additionalCommandHandling 
    
  /**
   * Standard command handling functionality where common logic for all entity types lives
   */
  def standardCommandHandling:Receive = {
    
    //Have been idle too long, time to start the passivation process
    case ReceiveTimeout =>
      log.info("{} entity with id {} is being passivated due to inactivity", entityType, id)
      context.parent ! Passivate(stopMessage = StopEntity)
      
    //Finishes the two part passivation process by stopping the entity
    case StopEntity =>
      log.info("{} entity with id {} is now being stopped due to inactivity", entityType, id)
      context stop self        
    
    //Don't allow actions on deleted entities or a non-create request
    //when in the initial state
    case any if !isAcceptingCommand(any) =>
      log.warning("Not allowing action {} on a deleted entity or an entity in the initial state with id {}", any, id)
      sender() ! stateResponse()      
          
    //Standard request to get the current state of the entity instance
    case GetState(id) =>
      sender ! stateResponse()
      
    //Standard handling logic for a request to mark the entity instance  as deleted
    case MarkAsDeleted =>
      //Only if a delete event is defined do we perform the delete.  This
      //allows some entities to not support deletion
      newDeleteEvent match{
        case None =>
          log.info("The entity type {} does not support deletion, ignoring delete request", entityType)
          sender ! stateResponse()
          
        case Some(event) =>
          persist(event)(handleEventAndRespond(false))
      }
      
    case s:SaveSnapshotSuccess =>
      log.info("Successfully saved a new snapshot for entity {} and id {}", entityType, id)
      
    case f:SaveSnapshotFailure =>
      log.error(f.cause, "Failed to save a snapshot for entity {} and id {}, reason was {}", entityType)
  }
  
  /**
   * Determines if the actor can accept the supplied command.  Can't
   * be deleted and if we are in initialState then it can
   * only be the create message
   * @param cmd The command to check
   * @return a Boolean indicating if we can handle the command
   */
  def isAcceptingCommand(cmd:Any) = 
    !state.deleted && 
      !(state == initialState && !isCreateMessage(cmd))
  
  /**
   * Implement in the subclass to provide the command handling logic that is
   * specific to this entity class
   */
  def additionalCommandHandling:Receive
  
  /**
   * Returns an optional delete event message to use when
   * a request to delete happens.  Returns None by default
   * indicating that no delete is supported
   * @return an Option for EntityEvent indicating what event to log for a delete
   */
  def newDeleteEvent:Option[EntityEvent] = None
  
  /**
   * Returns true if the message is the initial create message
   * which is the only command allowed when in the initial state
   * @param cmd The command to check
   * @return a Boolean indicating if this is the create request
   */
  def isCreateMessage(cmd:Any):Boolean
  
  /**
   * Returns the initial state of the fields object representing the state for this 
   * entity instance.  This will be the initial state before the very first persist call
   * and also the initial state before the recovery process kicks in
   * @return an instance of FO which is the fields object for this entity
   */
  def initialState:FO
  
  /**
   * Returns the result to send back to the sender when 
   * a request to get the current entity state happens
   * @param respectDeleted A boolean that if true means a deleted
   * entity will return en EmptyResult
   * @return a ServiceResult for FO
   */
  def stateResponse(respectDeleted:Boolean = true) = 
    //If we have not persisted this entity yet, then EmptyResult
    if (state == initialState) EmptyResult
    
    //If respecting deleted and it's marked deleted, EmptyResult
    else if (respectDeleted && state.deleted) EmptyResult
    
    //Otherwise, return it as a FullResult
    else FullResult(state)     
    
  /**
   * Implement in a subclass to provide the logic to update the internal state
   * based on receiving an event.  This can be either in recovery or
   * after persisting
   */
  def handleEvent(event:EntityEvent):Unit
  
  /**
   * Handles an event (via handleEvent) and the responds with the current state
   * @param respectDeleted A boolean that if true means a deleted entity will be returned as EmptyResult
   */
  def handleEventAndRespond(respectDeleted:Boolean = true)(event:EntityEvent):Unit = {
    handleEvent(event)
    if (snapshotAfterCount.isDefined){
      eventsSinceLastSnapshot += 1
      maybeSnapshot
    }
    sender() ! stateResponse(respectDeleted)
  }
  
  /**
   * Override in subclass to indicate when to take a snapshot based on eventsSinceLastSnapshot
   * @return an Option that will ne a Some if snapshotting should take place for this entity
   */
  def snapshotAfterCount:Option[Int] = None
  
  /**
   * Decides if a snapshot is to take place or not after a new event has been processed
   */
  def maybeSnapshot:Unit = {
    snapshotAfterCount.
      filter(i => eventsSinceLastSnapshot  >= i).
      foreach{ i =>
        log.info("Taking snapshot because event count {} is > snapshot event limit of {}", eventsSinceLastSnapshot, i)
        saveSnapshot(state)
        eventsSinceLastSnapshot = 0
      }
  }
}

/**
 * Abstract class to represent an Aggregate Actor that sits in front of entities and delegates requests to them
 */
abstract class Aggregate[FO <: EntityFieldsObject[String, FO], E <: PersistentEntity[FO] : ClassTag] extends BookstoreActor{
  
  val idExtractor = PersistentEntity.PersistentEntityIdExtractor(context.system)
  val entityShardRegion = 
    ClusterSharding(context.system).start(
      typeName = entityName,
      entityProps = entityProps, 
      settings = ClusterShardingSettings(context.system), 
      extractEntityId = idExtractor.extractEntityId, 
      extractShardId = idExtractor.extractShardId 
    )
    
  /**
   * Gets the Props needed to create the child entity for this factory
   * @return a Props instance
   */
  def entityProps:Props
  
  /**
   * Gets the name of the entity that this factory manages
   * @return a String
   */
  private def entityName = {
    val entityTag = implicitly[ClassTag[E]]
    entityTag.runtimeClass.getSimpleName()
  }
  
  def forwardCommand(id:String, command:Any) = 
    entityShardRegion.forward(command)
}

/**
 * Trait to mix into case classes that represent lightweight representations of the fields for
 * an entity modeled as an actor
 */
trait EntityFieldsObject[K, FO] extends Serializable{
  /**
   * Assigns an id to the fields object, returning a new instance
   * @param id The id to assign
   */
  def assignId(id:K):FO
  def id:K
  def deleted:Boolean
  def markDeleted:FO
}