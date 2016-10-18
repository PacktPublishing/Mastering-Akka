package com.packt.masteringakka.bookstore.common

import scala.concurrent.ExecutionContext
import com.typesafe.conductr.lib.akka.ConnectionContext
import com.typesafe.conductr.bundlelib.akka.LocationService
import com.typesafe.conductr.bundlelib.scala._
import com.typesafe.conductr.bundlelib.scala.CacheLike
import spray.json._
import akka.http.scaladsl.model._
import headers._
import akka.http.scaladsl.Http
import akka.stream._
import akka.http.scaladsl.unmarshalling._
import scala.concurrent.Future


/**
 * Represents the result from looking up a service in the ConductR environment
 */
case class ServiceLookupResult(name:String, uriOpt:Option[java.net.URI])

/**
 * Companion to the ServiceConsumer trait
 */
object ServiceConsumer{
  val GlobalCache = new LocationCache()
}

/**
 * Trait to mix into classes that need to consume other services in the ConductR environment
 */
trait ServiceConsumer extends ApiResponseJsonProtocol { me:BookstoreActor =>
  
  implicit val httpMater = ActorMaterializer()
  val http = Http(context.system)
  
  /**
   * Looks up a service within the ConductR environment
   * @param serviceName The name of the service
   * @param cache The cache to use
   * @param
   */
  def lookupService(serviceName:String, cache:CacheLike = ServiceConsumer.GlobalCache)(implicit ec:ExecutionContext, cc:ConnectionContext) = {    
    LocationService.
      lookup(serviceName, URI("http://localhost:8080/"), cache).
        map(opt => ServiceLookupResult(serviceName, opt)).
        andThen(lookupLogging(serviceName))
  }
  
  /**
   * Produces a partial function to perform logging of the lookup result
   * @param serviceName The name of the service that was looked up
   * @return a PartialFunction to plug into a Future andThen call
   */
  def lookupLogging(serviceName:String):PartialFunction[util.Try[ServiceLookupResult],Unit] = {
    case util.Success(ServiceLookupResult(name, Some(uri))) =>
      log.info("Successfully looked up service {}, uri is: {}", name, uri)
    case other =>
      log.error("Non successful service lookup for service {}, result is: {}", serviceName, other)    
  }
  
  /**
   * Generic method to make a request to a service over http and get the resulting json entity.  If the
   * request was not successful (non 200 level response) then the future will be failed
   * @param request The request to execute
   * @return a Future for type T
   */
  def executeHttpRequest[T:RootJsonFormat](request:HttpRequest) = {
    import context.dispatcher
    for{
      resp <- http.singleRequest(request).flatMap(successOnly(request))
      entity <- Unmarshal(resp.entity).to[ApiResponse[T]]
      if entity.response.isDefined
    } yield entity.response.get 
  }
  
  /**
   * Inspects the result of the a request to make sure it was successful, producing a failed future if not
   * @param req The request that was executed
   * @param resp The response to inspect
   * @return a Future wrapping the response if successful or else a failed future if not
   */
  def successOnly(req:HttpRequest)(resp:HttpResponse) = 
    if (resp.status.isSuccess) Future.successful(resp)
    else Future.failed(new RuntimeException(s"Non successful http status code of ${resp.status} received for request to uri: ${req.uri}"))  
}