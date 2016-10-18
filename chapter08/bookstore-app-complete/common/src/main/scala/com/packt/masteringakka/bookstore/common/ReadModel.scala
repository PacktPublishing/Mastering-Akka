package com.packt.masteringakka.bookstore.common

import akka.actor.Stash
import akka.persistence.query.PersistenceQuery
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.stream.ActorMaterializer
import java.util.Date
import scala.concurrent.Future
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import akka.persistence.query.EventEnvelope
import akka.stream.Supervision
import scala.util.control.NonFatal
import akka.stream.ActorMaterializerSettings
import spray.json.JsonFormat
import scala.reflect.ClassTag
import akka.actor.Props

trait ReadModelObject extends AnyRef{
  def id:String
}

object ViewBuilder{
  import ElasticsearchApi._
  
  sealed trait IndexAction
  case class UpdateAction(id:String, expression:List[String], params:Map[String,Any]) extends IndexAction
  object UpdateAction{
    def apply(id:String, expression:String, params:Map[String,Any]):UpdateAction = 
      UpdateAction(id, List(expression), params)
  }
  case class EnvelopeAndAction(env:EventEnvelope, action:IndexAction)
  case class EnvelopeAndFunction(env:EventEnvelope, f: () => Future[IndexingResult])
  case class InsertAction(id:String, rm:ReadModelObject) extends IndexAction
  case class NoAction(id:String) extends IndexAction
  case class DeferredCreate(flow:Flow[EnvelopeAndAction,EnvelopeAndAction,akka.NotUsed]) extends IndexAction
  case class LatestOffsetResult(offset:Option[Long])
}

abstract class ViewBuilder[RM <: ReadModelObject : ClassTag] extends BookstoreActor with ElasticsearchSupport{
  import context.dispatcher
  import ViewBuilder._
  import ElasticsearchApi._
  import akka.pattern.pipe
  
  //Set up the persistence query to listen for events for the target entity type
  val journal = PersistenceQuery(context.system).
    readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
    
  val decider: Supervision.Decider = {
    case NonFatal(ex) =>
      log.error(ex, "Got non fatal exception in ViewBuilder flow")
      Supervision.Resume
    case ex  => 
      log.error(ex, "Got fatal exception in ViewBuilder flow, stream will be stopped")
      Supervision.Stop
  }  
  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(context.system).
      withSupervisionStrategy(decider)
  )
  implicit val rmFormats:JsonFormat[RM]
  
  val resumableProjection = ResumableProjection(projectionId, context.system)
  resumableProjection.
    fetchLatestOffset.
    map(LatestOffsetResult.apply).    
    pipeTo(self)
    
  val eventsFlow = 
    Flow[EventEnvelope].
      map{ env =>
        val id = env.persistenceId.toLowerCase().drop(entityType.length() + 1)   
        EnvelopeAndAction(env, actionFor(id, env))          
      }.
      flatMapConcat{
        case ea @ EnvelopeAndAction(env, cr:DeferredCreate) => 
          Source.single(ea).via(cr.flow )
            
        case ea:EnvelopeAndAction => 
          Source.single(ea).via(Flow[EnvelopeAndAction])
      }.
      collect{
        case EnvelopeAndAction(env, InsertAction(id, rm:RM)) =>
          EnvelopeAndFunction(env, () => updateIndex(id, rm, None))            
          
        case EnvelopeAndAction(env, u:UpdateAction) =>
          EnvelopeAndFunction(env, () => updateDocumentField(u.id, env.sequenceNr - 1, u.expression, u.params))
        
        case EnvelopeAndAction(env, NoAction(id)) =>
          EnvelopeAndFunction(env, () => updateDocumentField(id, env.sequenceNr - 1, Nil, Map.empty[String,Any]))
      }.
      mapAsync(1){
        case EnvelopeAndFunction(env, f) => f.apply.map(_ => env)
      }.
      mapAsync(1)(env => resumableProjection.storeLatestOffset(env.offset))  
  
  def projectionId:String
  
  def actionFor(id:String, env:EventEnvelope):IndexAction
  
  def receive = {
    case LatestOffsetResult(o) =>      
      val offset = o.getOrElse(0L)
      if (offset == 0){
        clearIndex
      }
      
      val offsetDate = new Date(offset)
      log.info("Starting up view builder for entity {} with offset time of {}", entityType, offsetDate)
      val eventsSource = journal.eventsByTag(entityType, offset)
      eventsSource.
        via(eventsFlow).
        runWith(Sink.ignore)
  }
  
  def updateDocumentField(id:String, seq:Long, expressions:List[String], params:Map[String,Any]):Future[IndexingResult] = {
    val script = expressions.map(e => s"ctx._source.$e").mkString(";")    
    val request = UpdateRequest(UpdateScript(script, params))
    updateIndex(id, request, Some(seq))    
  }  
  
}