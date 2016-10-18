package code

import akka.stream.scaladsl.Source

import scala.concurrent.Future

object AsyncBoundaries extends AkkaStreamsApp {
  override def akkaStreamsExample: Future[_] =
    Source(1 to 5)
      .log("pre-map")
      .map(_ * 3)
      .async
      .log("pre-filter")
      .filter(_ % 2 == 0)
      .runForeach(x => log.debug(s"done: $x"))

  runExample
}