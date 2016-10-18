package com.packt.masteringakka.bookstore.credit

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import com.packt.masteringakka.bookstore.common.BookstoreRoutesDefinition
import com.packt.masteringakka.bookstore.common.BookstoreJsonProtocol
import akka.stream.Materializer
import akka.http.scaladsl.server.Route


/**
 * Http routes class for performing credit related actions
 */
class CreditRoutes(assoc:ActorRef)(implicit val ec:ExecutionContext) extends BookstoreRoutesDefinition with CreditJsonProtocol{
  import akka.http.scaladsl.server.Directives._
  import CreditAssociate._
  
  
  def routes(implicit system:ActorSystem, ec:ExecutionContext, mater:Materializer):Route = {
    pathPrefix("credit"){
      path(Segment){ id =>
        get{
          serviceAndComplete[CreditCardTransactionFO](FindTransactionById(id), assoc)
        }      
      } ~
      pathEndOrSingleSlash{
        post{
          entity(as[ChargeCreditCard]){ input =>
            serviceAndComplete[CreditCardTransactionFO](input, assoc)
          }
        }
      }      
    } 
  }
}