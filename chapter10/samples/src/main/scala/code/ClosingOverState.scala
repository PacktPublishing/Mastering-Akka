package code

import akka.actor._
import scala.concurrent.Future
import akka.util.Timeout

object ClosingOverState extends App{

  object UnsafeActor{
    def props = Props[UnsafeActor]
    case object GetCount
    case object UpdateCount
  }
  
  class UnsafeActor extends Actor{
    import UnsafeActor._
    import context.dispatcher
    import akka.pattern.pipe
    
    var count = 0
    
    def receive = {
      case GetCount =>
        sender() ! count
        
      case UpdateCount =>
        
        Future{0}.
          onComplete{
            case tr => 
              count = count + 1
          }
        
        sender() ! count
    }
  }
  
  object SafeActor{
    def props = Props[SafeActor]
    case object GetCount
    case object UpdateCount
    private object FinishUpdate
  }  
  
  class SafeActor extends Actor{
    import SafeActor._
    import context.dispatcher
    import akka.pattern.pipe
    
    var count = 0
    
    def receive = {
      case GetCount =>
        sender() ! count
        
      case UpdateCount =>
        
        Future{0}.
          onComplete{
            case tr => 
              self ! FinishUpdate
          }
        
        sender() ! count
        
      case FinishUpdate =>
        count = count + 1
    }
  }
  
  object SerialSafeActor{
    def props = Props[SafeActor]
    case object GetCount
    case object UpdateCount
    private object FinishUpdate
  }  
  
  class SerialSafeActor extends Actor with Stash{
    import SerialSafeActor._
    import context.dispatcher
    import akka.pattern.pipe
    
    var count = 0
    
    def receive = {
      case GetCount =>
        sender() ! count
        
      case UpdateCount =>
        
        Future{0}.
          onComplete{
            case tr => 
              self ! FinishUpdate
          }       
        
        context.become{
          case FinishUpdate =>
            count = count + 1
            unstashAll
            context.unbecome
          
          case other =>
            stash
        }
        
        sender() ! count
    }
  }  
  
  val system = ActorSystem()
  import system.dispatcher
  import akka.pattern.ask
  import concurrent.duration._
  import UnsafeActor._
  
  val ref = system.actorOf(SafeActor.props)
  implicit val timeout = Timeout(10 seconds)
  val fut = Future.traverse(1 to 1000)(_ => ref ? UpdateCount)
  
  for{
    _ <- fut
    count <- (ref ? GetCount).mapTo[Int]
  }{
    println(s"the count is: $count")
  }  
}