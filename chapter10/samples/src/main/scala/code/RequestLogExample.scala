package code

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import scala.concurrent.Future
import java.util.UUID
import akka.http.scaladsl.server._
import directives._
import akka.event._
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import java.time.format._
import java.time._
import java.util.Date
import java.nio.charset.Charset

case class Book(id:String, title:String)

object RequestLogExample extends App with SprayJsonSupport with DefaultJsonProtocol{
  val conf = ConfigFactory.parseString("""
      akka.http.server.remote-address-header=on
      akka.loggers = ["akka.event.slf4j.Slf4jLogger"]
  """)
  
  implicit val system = ActorSystem("logtest", conf)
  implicit val mater = ActorMaterializer()
  
  import Directives._
  implicit val bookFormat = jsonFormat2(Book)
  val accessLog = Logging(system.eventStream, "AccessLog")
  val formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");
  
  val routes =   
    parameter('sessionToken.?){ tokenOpt =>      
      identifyUser(tokenOpt){ userIdOpt =>        
        accessLogging(userIdOpt.getOrElse("-"), accessLog){          
          handleRejections(RejectionHandler.default){
            path("api" / "book" / Segment){ bookId =>
              complete(Book(bookId, "This is a test"))              
            }            
          }          
        }
      }       
    }
  
  Http().bindAndHandle(routes, "localhost", 8080)
  
  def identifyUser(sessionTokenOpt:Option[String])(f:Option[String] => Route) = 
    sessionTokenOpt.fold(f(None)){ sessionToken =>
      //This is where you could do session validation and coorelate
      //a session token with a user id
      val fut = Future.successful(UUID.randomUUID().toString)
      onComplete(fut)(tr => f(tr.toOption))
    }
  
  def accessLogging(userId:String, log:LoggingAdapter):Directive0 = {
    extractClientIP.flatMap{ ip =>
      logRequestResult(
        LoggingMagnet(_ => requestTimeThenLog(ip, userId, log))
      )
    }    
  }
  
  def requestTimeThenLog(ip:RemoteAddress, userId:String, 
    log:LoggingAdapter):HttpRequest => (Any => Unit) = {
    val requestTime = System.currentTimeMillis()
    buildLogString(log, requestTime, ip, userId) _
  }
  
  def buildLogString(loggingAdapter:LoggingAdapter, 
    requestTimestamp: Long, ip:RemoteAddress, 
    userId:String, level:Logging.LogLevel = Logging.InfoLevel)
    (req: HttpRequest)(res: Any): Unit = {
    val elapsed = System.currentTimeMillis() - requestTimestamp        
    val (status, contentLength) = res match {
      case RouteResult.Complete(resp) =>
        (resp.status.intValue, resp.entity.contentLengthOption.getOrElse(0))
      case RouteResult.Rejected(reason) =>
        //Should't get here due to explicit handleRejections in tree
        //but need to handle anyway
        (0, 0)
    }
    
    val ipAddress = ip.toOption.map(_.getHostAddress()).getOrElse("0.0.0.0")
    val dateTime = ZonedDateTime.now().format(formatter) 
    val path = req.uri.path
    val params = 
      req.uri.queryString(Charset.defaultCharset()).
        map(q => s"?$q").getOrElse("")
    val proto = req.protocol.value
        
    val logLine = s"""$ipAddress - $userId [$dateTime] "${req.method.name()} $path$params $proto" $status $contentLength $elapsed"""
    LogEntry(logLine, level).logTo(loggingAdapter)
  }  
}


