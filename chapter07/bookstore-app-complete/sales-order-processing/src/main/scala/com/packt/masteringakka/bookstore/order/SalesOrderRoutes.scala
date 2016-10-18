package com.packt.masteringakka.bookstore.order

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.actor.ActorRef
import com.packt.masteringakka.bookstore.common.BookstoreRoutesDefinition
import com.packt.masteringakka.bookstore.common.BookstoreJsonProtocol
import akka.stream.Materializer
import akka.http.scaladsl.server.Route
import spray.json.RootJsonWriter
import spray.json.JsString
import org.json4s.ext.EnumNameSerializer
import spray.json.RootJsonFormat
import spray.json.JsValue
import spray.json.DeserializationException
import SalesOrderViewBuilder._
import com.packt.masteringakka.bookstore.credit.CreditCardInfo
import SalesAssociate._
import SalesOrder._

/**
 * Http routes class for sales order related actions
 */
class SalesOrderRoutes(salesAssociate:ActorRef, salesOrderView:ActorRef)(implicit val ec:ExecutionContext) extends BookstoreRoutesDefinition with OrderJsonProtocol{  
  import SalesOrderView._
  import akka.http.scaladsl.server.Directives._
  
  def routes(implicit system:ActorSystem, ec:ExecutionContext, mater:Materializer):Route = {
    pathPrefix("order"){
      get{
        path(Segment){ id =>
          serviceAndComplete[SalesOrderFO](FindOrderById(id), salesAssociate)
        } ~
        pathEndOrSingleSlash{
          parameter('email){ email =>
            serviceAndComplete[List[SalesOrderRM]](FindOrdersForUser(email), salesOrderView)
          } ~
          parameter('bookId){ bookId =>
            serviceAndComplete[List[SalesOrderRM]](FindOrdersForBook(bookId), salesOrderView)
          } ~ 
          parameter('bookTag){ tag =>
            serviceAndComplete[List[SalesOrderRM]](FindOrdersForBookTag(tag), salesOrderView)
          }
        }
      } ~
      post{
        entity(as[CreateNewOrder]){ createReq =>
          serviceAndComplete[SalesOrderFO](createReq, salesAssociate)
        }
      }
    }
  }
}