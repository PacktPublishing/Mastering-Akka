package code

import akka.stream.Fusing
import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.Future

object StreamFusion extends AkkaStreamsApp {

  val flow =
    Flow[Int].
      map(_ * 3).
      filter(_ % 2 == 0)
  val fused = Fusing.aggressive(flow)

  override def akkaStreamsExample: Future[_] =
    Source(List(1, 2, 3, 4, 5))
      .via(fused)
      .log("fused")
      .runForeach(e => log.debug("Received: {}", e))

  runExample
}