package code

import akka.actor._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import com.typesafe.config.ConfigFactory
import akka.stream.scaladsl.Source
import akka.event.Logging
import akka.stream.scaladsl.Sink
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.nio.charset.Charset

object ExternalCallLogExample extends App{
  val conf = ConfigFactory.parseString("""akka.loggers = ["akka.event.slf4j.Slf4jLogger"]""")
  
  implicit val system = ActorSystem("logtest", conf)
  implicit val mater = ActorMaterializer() 
  val externalCallLog = Logging(system.eventStream, "ExternalCallLog")
  val regularLog = Logging(system.eventStream, "RegularLog")
  val formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");
  
  val connPool = Http().superPool[Int]()
  
  val time1 = System.currentTimeMillis()
  val request = HttpRequest(HttpMethods.GET, "http://akka.io")
  
  
  Source.
    single((request, 1)).
    via(connPool).
    map(logCall(time1, "abc123", Some(request))).
    runWith(Sink.head)
      
  val time2 = System.currentTimeMillis()
  val request2 = HttpRequest(HttpMethods.GET, "http://stackoverflow.com/users/2311148/cmbaxter")
  val requests = Map(1 -> request, 2 -> request2)
  Source(requests.toList.map(t => (t._2, t._1))).
    via(connPool).
    map(r => logCall(time2, "abc123", requests.get(r._2))(r)).
    runFold(Map.empty[Int, util.Try[HttpResponse]]){
      case(m, r) => m ++ Map(r._2 -> r._1) 
    }
  
  val time3 = System.currentTimeMillis()
  val conn = Http().outgoingConnection("stackoverflow.com")
  Source.
    single(request2).
    via(conn).
    map(logSimpleCall(time3, "abc123", request2)).
    runWith(Sink.head)
    
  val time4 = System.currentTimeMillis()
  import system.dispatcher
  Http().
    singleRequest(request2).
    andThen{
      case util.Success(resp) =>
        logSimpleCall(time4, "abc123", request2)(resp)
    }.
    andThen{
      case _ =>
        system.terminate
    }
    
  
  def logSimpleCall(startTime:Long, userId:String, request:HttpRequest)(result:HttpResponse):HttpResponse = {
    logCall(startTime, userId, Some(request))((util.Success(result), 1))
    result
  }
  
  def logCall[T](startTime:Long, userId:String, 
    request:Option[HttpRequest])(result:(util.Try[HttpResponse], T)):(util.Try[HttpResponse], T) = {
    val response = result._1
    val elapsed = System.currentTimeMillis() - startTime
    (request, response) match{
      case (Some(req), util.Success(resp)) =>
        val host = req.uri.authority.host 
        val status = resp.status.intValue
        val contentLength = resp.entity.contentLengthOption.getOrElse(0)
        val dateTime = ZonedDateTime.now().format(formatter) 
        val path = req.uri.path
        val params = 
          req.uri.queryString(Charset.defaultCharset()).
            map(q => s"?$q").getOrElse("")
        val proto = req.protocol.value
        
        val logLine = s"""$host - $userId [$dateTime] "${req.method.name()} $path$params $proto" $status $contentLength $elapsed"""        
        externalCallLog.info(logLine)
        
      case (req, resp) =>
        regularLog.error("Error making http call, did not have both a request and response: {}, {}", req, resp)
    }
    result
  }
}