package com.packt.masteringakka.bookstore.common

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor._
import akka.event.Logging
import com.datastax.driver.core._

import scala.concurrent.Future

/**
 * Interface into a projection's offset storage system so that it can be properly resumed
 */
abstract class ResumableProjection(identifier:String) {
  def storeLatestOffset(offset:Long):Future[Boolean]
  def fetchLatestOffset:Future[Option[Long]]
}

object ResumableProjection{
  def apply(identifier:String, system:ActorSystem) = 
    new CassandraResumableProjection(identifier, system)
}

class CassandraResumableProjection(identifier:String, system:ActorSystem) extends ResumableProjection(identifier){
  val projectionStorage = CassandraProjectionStorage(system)
  
  def storeLatestOffset(offset:Long):Future[Boolean] = {
    projectionStorage.updateOffset(identifier, offset + 1)
  }
  def fetchLatestOffset:Future[Option[Long]] = {
    projectionStorage.fetchLatestOffset(identifier)
  }
}

class CassandraProjectionStorageExt(system:ActorSystem) extends Extension {
  import akka.persistence.cassandra.listenableFutureToFuture
  import system.dispatcher

  val cassandraConfig = system.settings.config.getConfig("cassandra")
  implicit val log = Logging(system.eventStream, "CassandraProjectionStorage")

  var initialized = new AtomicBoolean(false)
  val createKeyspaceStmt = """
      CREATE KEYSPACE IF NOT EXISTS bookstore
      WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
    """

  val createTableStmt = """
      CREATE TABLE IF NOT EXISTS bookstore.projectionoffsets (
        identifier varchar primary key, offset bigint)
  """

  val init: Session => Future[Unit] = (session: Session) => for {
    _ <- session.executeAsync(createKeyspaceStmt)
    _ <- session.executeAsync(createTableStmt)
  } yield ()

  val session = new CassandraSession(system, cassandraConfig, init)

  def updateOffset(identifier:String, offset:Long): Future[Boolean] = (for {
    session <- session.underlying()
    _ <- session.executeAsync(s"update bookstore.projectionoffsets set offset = $offset where identifier = '$identifier'")
  } yield true) recover { case t => false }

  def fetchLatestOffset(identifier:String): Future[Option[Long]] = for {
    session <- session.underlying()
    rs <- session.executeAsync(s"select offset from bookstore.projectionoffsets where identifier = '$identifier'")
  } yield {
    import collection.JavaConversions._
    rs.all().headOption.map(_.getLong(0))
  }
}
object CassandraProjectionStorage extends ExtensionId[CassandraProjectionStorageExt] with ExtensionIdProvider { 
  override def lookup = CassandraProjectionStorage 
  override def createExtension(system: ExtendedActorSystem) =
    new CassandraProjectionStorageExt(system)
}