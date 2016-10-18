package com.packt.masteringakka.bookstore.common

import akka.actor._
import com.packt.masteringakka.bookstore._
import scala.concurrent.Future

/**
 * Base actor definition for other actors in the bookstore app to extend from
 */
trait BookStoreActor extends Actor with ActorLogging{
  import akka.pattern.pipe
  import context.dispatcher
  
  //PF to be used with the .recover combinator to convert an exception on a failed Future into a
  //Failure ServiceResult
  private val toFailure:PartialFunction[Throwable, ServiceResult[Nothing]] = {
    case ex => Failure(FailureType.Service, ServiceResult.UnexpectedFailure, Some(ex))
  }
  
  /**
   * Pipes the response from a request to a service actor back to the sender, first
   * converting to a ServiceResult per the contract of communicating with a bookstore service
   * @param f The Future to map the result from into a ServiceResult
   */
  def pipeResponse[T](f:Future[T]):Unit = 
    f.
      map{
        case o:Option[_] => ServiceResult.fromOption(o) 
        case f:Failure => f
        case other => FullResult(other)
      }.
      recover(toFailure). 
      pipeTo(sender())
}