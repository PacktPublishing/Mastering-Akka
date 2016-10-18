package code

import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{ Directives, Route }
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.conductr.bundlelib.akka.{ Env, LocationService, StatusService }
import com.typesafe.conductr.bundlelib.scala.{ LocationCache, URI }
import com.typesafe.conductr.lib.akka.ConnectionContext
import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ ExecutionContext, Future }

object Main extends App with SprayJsonSupport with DefaultJsonProtocol with Directives {
  // getting bundle configuration from Conductr
  val config = Env.asConfig
  val systemName = sys.env.getOrElse("BUNDLE_SYSTEM", "StandaloneSystem")
  val systemVersion = sys.env.getOrElse("BUNDLE_SYSTEM_VERSION", "1")

  // configuring the ActorSystem
  implicit val system = ActorSystem(s"$systemName-$systemVersion", config.withFallback(ConfigFactory.load()))

  // setting up some machinery
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val cc = ConnectionContext()
  implicit val locationCache = LocationCache()

  val httpServerCfg = system.settings.config.getConfig("helloworld")
  val configuredIpAddress = httpServerCfg.getString("ip")
  val configuredPort = httpServerCfg.getInt("port")

  log.debug(" ==> Launching HelloWorld sample application on ip: '{}', port: '{}'", configuredIpAddress, configuredPort)

  final case class HelloWorldResponse(msg: String)

  final case class Person(name: String, age: Int)

  implicit val helloWorldJsonFormat = jsonFormat1(HelloWorldResponse)
  implicit val personJsonFormat = jsonFormat2(Person)

  def completeWithHello = extractMethod(method => complete(HelloWorldResponse(s"${method.value} Hello World!")))

  def queryServiceLocator(serviceName: String = "web") =
    LocationService.lookup(serviceName, URI("http://localhost:8080/"), locationCache)

  def route: Route =
    logRequestResult("chapter9-sample-helloworld") {
      path("helloworld") {
        (get & pathEnd)(completeWithHello) ~
          (put & pathEnd)(completeWithHello) ~
          (patch & pathEnd)(completeWithHello) ~
          (delete & pathEnd)(completeWithHello) ~
          (options & pathEnd)(completeWithHello)
      } ~
        path("person") {
          (post & pathEnd & entity(as[Person])) { person =>
            complete(person)
          } ~
            (get & pathEnd) {
              complete(Person("John Doe", 40))
            }
        } ~
        (get & path("service" / Segment)) { serviceName =>
          complete(queryServiceLocator(serviceName).map {
            case Some(uri) => s"Service '$serviceName' found, its available at: $uri"
            case _         => s"No service found for service name: '$serviceName'"
          })
        }
    }

  (for {
    _ <- Http().bindAndHandle(route, interface = configuredIpAddress, port = configuredPort)
    _ <- StatusService.signalStartedOrExit()
  } yield ()).recover {
    case cause: Throwable =>
      log.error(cause, "Failure while launching HelloWorld")
      StatusService.signalStartedOrExit()
  }
}