package code

import akka.stream.{OverflowStrategy, ThrottleMode}
import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.concurrent.duration._

object ExplicitBuffer extends AkkaStreamsApp {
  override def akkaStreamsExample: Future[_] =
    Source(1 to 100000000)
      .log("passing")
      .buffer(5, OverflowStrategy.backpressure)
      .throttle(1, 1 second, 1, ThrottleMode.shaping)
      .take(20)
      .runForeach(e => log.debug("received: {}", e))
  
  runExample
}