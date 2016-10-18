package com.packt.masteringakka.bookstore.common

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.typesafe.config.Config
import akka.actor._
import com.datastax.driver.core._
import akka.Done
import akka.event.Logging
import java.util.concurrent.atomic.AtomicBoolean
import java.net.InetSocketAddress

/**
 * Interface into a projection's offset storage system so that it can be properly resumed
 */
abstract class ResumableProjection(identifier:String){
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

class CassandraProjectionStorageExt(system:ActorSystem) extends Extension{
  import system.dispatcher
  import collection.JavaConversions._
  import akka.persistence.cassandra.listenableFutureToFuture
  
  val log = Logging(system.eventStream, "CassandraProjectionStorage")
  var initialized = new AtomicBoolean(false)
  val createKeyspaceStmt = """
      CREATE KEYSPACE IF NOT EXISTS bookstore
      WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
    """

  val createTableStmt = """
      CREATE TABLE IF NOT EXISTS bookstore.projectionoffsets (
        identifier varchar primary key, offset bigint)  
  """  
  
  val poolingOptions = new PoolingOptions()  
  val contactPoint = system.settings.config.getStringList("cassandra-journal.contact-points").toList.head
  val contactPort = system.settings.config.getInt("cassandra-journal.port")
  log.info("Connesting to contact point: {}", contactPoint )
  val cluster = 
    Cluster.builder().
    addContactPointsWithPorts(new InetSocketAddress(contactPoint, contactPort)).
    withPoolingOptions(poolingOptions).
    build()
    
  val session = cluster.connect()
  val keyspace:Future[Done] = session.executeAsync(createKeyspaceStmt ).map(toDone)
  val result = 
    for{
      _ <- keyspace
      done <- session.executeAsync(createTableStmt)
    } yield done
    
  result onComplete{
    case util.Success(_) =>
      log.info("Successfully initialized the projection storage system")
      initialized.set(true)
      
    case util.Failure(ex) =>
      log.error(ex, "Error initializing projection storage system")
  }
    
  def updateOffset(identifier:String, offset:Long):Future[Boolean] = {
    val fut:Future[_] = 
      session.executeAsync(s"update bookstore.projectionoffsets set offset = $offset where identifier = '$identifier'")
    fut.map(_ => true).recover{case ex => false}
  }
  
  def fetchLatestOffset(identifier:String):Future[Option[Long]] = {
    import collection.JavaConversions._
    val f:Future[ResultSet] = session.executeAsync(s"select offset from bookstore.projectionoffsets where identifier = '$identifier'")
    f.map{ rs =>
      rs.all().headOption.map(_.getLong(0))
    }
  }

  def isInitialized = initialized.get();
  
  def toDone(a:Any):Done = Done

}
object CassandraProjectionStorage extends ExtensionId[CassandraProjectionStorageExt] with ExtensionIdProvider { 
  override def lookup = CassandraProjectionStorage 
  override def createExtension(system: ExtendedActorSystem) =
    new CassandraProjectionStorageExt(system)
}