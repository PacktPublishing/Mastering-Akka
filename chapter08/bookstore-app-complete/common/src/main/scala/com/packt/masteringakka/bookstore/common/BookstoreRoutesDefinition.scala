package com.packt.masteringakka.bookstore.common

import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import akka.stream.Materializer
import akka.actor.ActorRef
import scala.reflect.ClassTag
import scala.concurrent.Future
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.StatusCodes._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import java.util.Date

object BookstoreRoutesDefinition{
  val NotFoundResp = ApiResponse[String](ApiResponseMeta(NotFound.intValue, Some(ErrorMessage("notfound"))))
  val UnexpectedFailResp = ApiResponse[String](ApiResponseMeta(InternalServerError.intValue, Some(ServiceResult.UnexpectedFailure )))
}

/**
 * Trait that represents a place where a set of routes for the bookstore app are constructed
 */
trait BookstoreRoutesDefinition extends ApiResponseJsonProtocol{
  import BookstoreRoutesDefinition._
  import concurrent.duration._
  implicit val endpointTimeout = Timeout(10 seconds)

  /**
   * Returns the routes defined for this endpoint
   * @param system The implicit system to use for building routes
   * @param ec The implicit execution context to use for routes
   * @param mater The implicit materializer to use for routes
   */
  def routes(implicit system:ActorSystem, ec:ExecutionContext, mater:Materializer):Route
  
  /**
   * Uses ask to send a request to an actor, expecting a ServiceResult back in return
   * @param msg The message to send
   * @param ref The actor ref to send to
   * @param timeout The implicit timeout to use for the ask
   * @return a Future for a ServiceResult for type T
   */
  def service[T :ClassTag](msg:Any, ref:ActorRef):Future[ServiceResult[T]] = {
    import akka.pattern.ask
    (ref ? msg).mapTo[ServiceResult[T]]
  }
  
  /**
   * Uses service to get a result and then inspects that result to complete the request
   * @param msg The message to send
   * @param ref The actor ref to send to
   * @param timeout The implicit timeout to use for the ask
   * @param marshaller The implicit marshaller to use for the response
   * @return a completed Route
   */
  def serviceAndComplete[T:ClassTag](msg:Any, ref:ActorRef)(implicit format:JsonFormat[T]):Route = {
    val fut = service[T](msg, ref)
    onComplete(fut){
      case util.Success(FullResult(t)) =>         
        val resp = ApiResponse(ApiResponseMeta(OK.intValue), Some(t))
        complete(resp)
        
      case util.Success(EmptyResult) =>         
        complete((NotFound, NotFoundResp))
        
      case util.Success(Failure(FailureType.Validation, ErrorMessage.InvalidEntityId, _)) =>
        complete((NotFound, NotFoundResp )) 
        
      case util.Success(fail:Failure) =>
        val status = fail.failType match{
          case FailureType.Validation => BadRequest
          case _ => InternalServerError
        }
        val apiResp = ApiResponse[String](ApiResponseMeta(status.intValue, Some(fail.message)))
        complete((status, apiResp))        
        
      case util.Failure(ex) =>
        complete((InternalServerError, UnexpectedFailResp))
    }
  }
}