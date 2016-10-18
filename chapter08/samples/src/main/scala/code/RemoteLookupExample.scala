package code

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import java.util.Date
import akka.actor.ActorLogging
import akka.actor.Props
import concurrent.duration._
import akka.util.Timeout

trait RemotingConfig{
  def remotingConfig(port:Int) = s"""
    akka {
      actor {
        provider = "akka.remote.RemoteActorRefProvider"
      }
      remote {
        enabled-transports = ["akka.remote.netty.tcp"]
        netty.tcp {
          hostname = "127.0.0.1"
          port = $port
        }
      }
    }
  """
}

object DateActor{
  case object GetCurrentDate
  case class CurrentDate(date:Date)
  def props = Props[DateActor]
}

class DateActor extends Actor with ActorLogging{
  import DateActor._
  def receive = {
    case GetCurrentDate =>
      val caller = sender()
      log.info("Received a request from {}", caller.path)
      caller ! CurrentDate(new Date)
  }
}

object ReceiverSystem extends App with RemotingConfig{
  val config = ConfigFactory.parseString(remotingConfig(2552))
    .withFallback(ConfigFactory.defaultApplication())
  val system = ActorSystem("ReceiverSystem", config)
  system.actorOf(DateActor.props, "dateActor")
}

object SenderSystem extends App with RemotingConfig{
  import DateActor._
  val config = ConfigFactory.parseString(remotingConfig(2553))
    .withFallback(ConfigFactory.defaultApplication())
  val system = ActorSystem("SenderSystem", config)
  val path = "akka.tcp://ReceiverSystem@127.0.0.1:2552/user/dateActor"
  val selection = system.actorSelection(path)
  
  implicit val timeout = Timeout(5 seconds)
  import akka.pattern.ask
  import system.dispatcher
  
  val result = 
    (selection ? GetCurrentDate).mapTo[CurrentDate]
    
  result.onComplete{ tr =>
    println(s"Received result from remote system: $tr")
    system.terminate
  }
}