package com.packt.masteringakka.bookstore.common

import akka.actor.FSM
import akka.actor.Stash
import akka.actor.Status
import scala.concurrent.Future
import akka.actor.Props
import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object EntityActor{
  case object GetFieldsObject
  case object Initialize
  case object Delete

  trait State
  case object Initializing extends State
  case object Initialized extends State
  case object Missing extends State
  case object Creating extends State
  case object Persisting extends State
  case object FailedToLoad extends State

  trait Data
  case object NoData extends Data
  case class InitializingData(id:Int) extends Data

  object NonStateTimeout{
    def unapply(any:Any) = any match{
      case FSM.StateTimeout => None
      case _ => Some(any)
    }
  }

  type ErrorMapper = PartialFunction[Throwable, Failure]

  case class Loaded[FO](fo:Option[FO])
  case class MissingData[FO](id:Int, deleted:Option[FO] = None) extends Data
  case class InitializedData[FO](fo:FO) extends Data
  case class PersistingData[FO](fo:FO, f:Int => FO, newInstance:Boolean = false) extends Data
  case class FinishCreate[FO](fo:FO)
}

abstract class EntityActor[FO <: EntityFieldsObject[FO]](idInput:Int) extends BookstoreActor with FSM[EntityActor.State, EntityActor.Data] with Stash{
  import EntityActor._
  import akka.pattern.pipe
  import concurrent.duration._
  import context.dispatcher

  val repo:EntityRepository[FO]
  val entityType = getClass.getSimpleName
  val errorMapper:ErrorMapper

  if (idInput == 0) {
    startWith(Creating, NoData)
  }
  else {
    startWith(Initializing, InitializingData(idInput))
    self ! Initialize
  }

  when(Initializing){
    case Event(Initialize, data:InitializingData) =>
      log.info("Initializing state data for {} {}", entityType, data.id )
      repo.loadEntity(data.id).map(fo => Loaded(fo)) pipeTo self
      stay

    case Event(Loaded(Some(fo)), _) =>
      unstashAll
      goto(Initialized) using InitializedData(fo)

    case Event(Loaded(None), data:InitializingData) =>
      log.error("No entity of type {} for id {}", entityType, idInput)
      unstashAll
      goto(Missing) using MissingData(data.id)

    case Event(Status.Failure(ex), data:InitializingData) =>
      log.error(ex, "Error initializing {} {}, stopping", entityType, data.id)
      goto(FailedToLoad) using data

    case Event(NonStateTimeout(other), _) =>
      stash
      stay
  }

  when(Missing, 1 second){
    case Event(GetFieldsObject, data:MissingData[FO]) =>
      val result = data.deleted.map(FullResult.apply).getOrElse(EmptyResult)
      sender ! result
      stay

    case Event(NonStateTimeout(other), _) =>
      sender ! Failure(FailureType.Validation, ErrorMessage.InvalidEntityId )
      stay
  }

  when(Creating)(customCreateHandling orElse standardCreateHandling)

  def customCreateHandling:StateFunction = PartialFunction.empty

  def standardCreateHandling:StateFunction = {
    case Event(fo:FO, _) =>
      createAndRequestFO(fo)
    case Event(FinishCreate(fo:FO), _) =>
      createAndRequestFO(fo)
    case Event(Status.Failure(ex), _) =>
      log.error(ex, "Failed to create a new entity of type {}", entityType)
      val fail = mapError(ex)
      goto(Missing) using MissingData(0) replying(fail)
  }

  def createAndRequestFO(fo:FO) = {
    requestFoForSender
    persist(fo, repo.persistEntity(fo), id => fo.assignId(id), true)
  }

  when(Initialized, 60 second)(standardInitializedHandling orElse initializedHandling)

  def standardInitializedHandling:StateFunction = {
    case Event(GetFieldsObject, InitializedData(fo)) =>
      sender ! FullResult(fo)
      stay

    case Event(Delete, InitializedData(fo: FO)) =>
      requestFoForSender
      persist(fo, repo.deleteEntity(fo.id), _ => fo.markDeleted)
  }

  def initializedHandling:StateFunction

  when(Persisting){
    case Event(i:Int, PersistingData(fo: FO, f: (Int => FO), newInstance)) =>
      val newFo: FO = f(i)
      unstashAll

      if (newFo.deleted){
        goto(Missing) using MissingData(newFo.id, Some(newFo))
      }
      else{
        if (newInstance) {
          postCreate(newFo)
          setStateTimeout(Initialized, Some(1 second))
        }
        goto(Initialized) using InitializedData(newFo)
      }

    case Event(Status.Failure(ex), data:PersistingData[FO]) =>
      log.error(ex, "Failed on an create/update operation to {} {}", entityType, data.fo.id)
      val response = mapError(ex)
      goto(Initialized) using InitializedData(data.fo) forMax(1 second) replying(response)

    case Event(NonStateTimeout(other), _) =>
      stash
      stay
  }

  when(FailedToLoad, 1 second){
    case Event(NonStateTimeout(other), _) =>
      sender ! Failure(FailureType.Service , ServiceResult.UnexpectedFailure)
      stay
  }

  whenUnhandled{
    case Event(StateTimeout, _) =>
      log.info("{} entity {} has reached max idle time, stopping instance", getClass.getSimpleName, self.path.name)
      stop
  }

  def persist(fo:FO, f: => Future[Int], foF: Int => FO, newInstance:Boolean = false) = {
    val daoResult = f
    daoResult.to(self, sender())
    goto(Persisting) using PersistingData(fo, foF, newInstance)
  }

  def postCreate(fo:FO){}

  def mapError(ex:Throwable) =
    errorMapper.lift(ex).getOrElse(Failure(FailureType.Service, ServiceResult.UnexpectedFailure ))


  def requestFoForSender:Unit = requestFoForSender(sender())
  def requestFoForSender(ref:ActorRef):Unit = self.tell(GetFieldsObject, ref)
}

/**
  * Trait to mix into case classes that represent lightweight representations of the fields for
  * an entity modeled as an actor
  */
trait EntityFieldsObject[FO]{
  /**
    * Assigns an id to the fields object, returning a new instance
    *
    * @param id The id to assign
    */
  def assignId(id:Int):FO
  def id:Int
  def deleted:Boolean
  def markDeleted:FO
}

abstract class EntityAggregate[FO <: EntityFieldsObject[FO], E <: EntityActor[FO] : ClassTag] extends BookstoreActor{
  def lookupOrCreateChild(id:Int): ActorRef = {
    val name = entityActorName(id)
    context.child(name).getOrElse{
      log.info("Creating new {} actor to handle a request for id {}", entityName, id)
      if (id > 0)
        context.actorOf(entityProps(id), name)
      else
        context.actorOf(entityProps(id))
    }
  }

  def persistOperation(id:Int, msg:Any){
    val entity = lookupOrCreateChild(id)
    entity.forward(msg)
  }

  def askForFo(bookActor:ActorRef) = {
    import akka.pattern.ask
    import concurrent.duration._
    implicit val timeout = Timeout(5 seconds)
    (bookActor ? EntityActor.GetFieldsObject).mapTo[ServiceResult[FO]]
  }

  def multiEntityLookup(f: => Future[Vector[Int]])(implicit ex:ExecutionContext) = {
    for{
      ids <- f
      actors = ids.map(lookupOrCreateChild)
      fos <- Future.traverse(actors)(askForFo)
    } yield{
      FullResult(fos.flatMap(_.toOption))
    }
  }


  def entityProps(id:Int):Props

  private def entityName = {
    val entityTag = implicitly[ClassTag[E]]
    entityTag.runtimeClass.getSimpleName()
  }
  private def entityActorName(id:Int) = {
    s"${entityName.toLowerCase}-$id"
  }

}