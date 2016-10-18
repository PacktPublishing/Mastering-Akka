package code

import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.concurrent.duration._

object BufferProblems extends AkkaStreamsApp {

  case class Tick()

  val fastSource = Source.tick(1 second, 1 second, Tick())
  val slowSource = Source.tick(3 second, 3 second, Tick())

  val asyncZip =
    Flow[Int]
      .zip(slowSource)
      .async

  override def akkaStreamsExample: Future[_] =
    fastSource
      .conflateWithSeed(seed = (_) => 1)((count, _) => count + 1)
      .log("Before AsyncZip")
      .via(asyncZip)
      .take(10)
      .log("After AsyncZip")
      .runForeach { case (i, t) => log.debug("Received: {}", i) }

  runExample
}