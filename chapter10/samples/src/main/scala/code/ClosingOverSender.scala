package code

import akka.actor._
import scala.concurrent.Future

object ClosingOverSender {

  
  class UnsafeActor extends Actor{
    def receive = {
      case _ =>
        import context.dispatcher
        Future{0}.
          onComplete{
            case tr =>
              sender() ! "foo"
          }
    }
  }
  
  class SafeActor extends Actor{
    def receive = {
      case _ =>
        import context.dispatcher
        val originator = sender()
        Future{0}.
          onComplete{
            case tr =>
              originator ! "foo"
          }
    }
  }
  
  class PipeSafeActor extends Actor{
    def receive = {
      case _ =>
        import context.dispatcher
        import akka.pattern.pipe
         
        Future{0}.
          map{ _ =>
            "foo"
          }.
          pipeTo(sender())
    }
  }  
}