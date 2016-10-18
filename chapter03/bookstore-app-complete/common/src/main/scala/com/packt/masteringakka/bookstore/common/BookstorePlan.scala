package com.packt.masteringakka.bookstore.common

import unfiltered.netty._
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import org.json4s.ext.EnumNameSerializer
import scala.concurrent.Future
import unfiltered.response._
import io.netty.handler.codec.http.HttpResponse

/**
 * Base trait for the endpoints in the bookstore app
 */
trait BookstorePlan extends async.Plan with ServerErrorResponse{
  import concurrent.duration._
  
  implicit val ec:ExecutionContext
  implicit val endpointTimeout = Timeout(10 seconds)
  implicit val formats = Serialization.formats(NoTypeHints) ++ additionalSerializers
  
  /**
   * Returns any custom serializers that a particular endpoint may need
   * @return a List of json4s serializers
   */
  def additionalSerializers:List[Serializer[_]] = Nil
  
  /**
   * Extractor for matching on a path element that is an Int
   */
  object IntPathElement{
    
    /**
     * Unapply to see if the path element is an Int
     * @param str The string path element to check
     * @return an Option that will be None if not an Int and a Some if an Int
     */
    def unapply(str:String) = util.Try(str.toInt).toOption
  }
  
  /**
   * Generic http response handling method to interpret the result of a Future into how to respond
   * @param f The Future to use the result from when responding
   * @param resp The responder to respond with
   */
  def respond(f:Future[Any], resp:unfiltered.Async.Responder[HttpResponse]) = {

    f.onComplete{
      
      //Got a good result that we can respond with as json
      case util.Success(FullResult(b:AnyRef)) => 
        resp.respond(asJson(ApiResponse(ApiResonseMeta(Ok.code), Some(b))))
        
      //Got an EmptyResult which will become a 404 with json indicating the not found
      case util.Success(EmptyResult) =>
        resp.respond(asJson(ApiResponse(ApiResonseMeta(NotFound.code, Some(ErrorMessage("notfound")))), NotFound)) 
        
      //Got an InvalidEntityError, that will translate into a 404
      case util.Success(Failure(FailureType.Validation, ErrorMessage.InvalidEntityId, _)) =>
        resp.respond(asJson(ApiResponse(ApiResonseMeta(NotFound.code, Some(ErrorMessage("notfound")))), NotFound))         
        
      //Got a Failure.  Will either be a 400 for a validation fail or a 500 for everything else
      case util.Success(fail:Failure) =>
        val status = fail.failType match{
          case FailureType.Validation => BadRequest
          case _ => InternalServerError
        }
        val apiResp = ApiResponse(ApiResonseMeta(status.code, Some(fail.message)))
        resp.respond(asJson(apiResp, status))    
        
      //Got a Success for a result type that is not a ServiceResult.  Respond with an unexpected exception
      case util.Success(x) =>
        val apiResp = ApiResponse(ApiResonseMeta(InternalServerError.code, Some(ServiceResult.UnexpectedFailure )))
        resp.respond(asJson(apiResp, InternalServerError))
             
      //The Future failed, so respond with a 500
      case util.Failure(ex) => 
        val apiResp = ApiResponse(ApiResonseMeta(InternalServerError.code, Some(ServiceResult.UnexpectedFailure )))
        resp.respond(asJson(apiResp, InternalServerError))     
    }
  }
  
  /**
   * Creates and returns an Unfiltered ResponseFunction to respond as json 
   * @param apiResp The api response to respond with
   * @param status A Status to respond with, defaulting to Ok if not supplied
   * @return An Unfiltered ResponseFunction
   */
  def asJson[T <: AnyRef](apiResp:ApiResponse[T], status:Status = Ok) = {
    val ser = write(apiResp)          
    status ~> JsonContent ~> ResponseString(ser)    
  }
  
  /**
   * Parses the supplied json String into a type specified by T
   * @param json The json to parse into type T
   * @return an instance of T
   */
  def parseJson[T <: AnyRef: Manifest](json:String) = read[T](json)     
}