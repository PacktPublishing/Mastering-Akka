package code

import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object MaterializationShortcuts extends AkkaStreamsApp {
  val source = Source(List(1, 2, 3, 4, 5)).log("source")
  val sink = Sink.fold[Int, Int](0)(_ + _)
  val multiplier = Flow[Int].map(_ * 2).log("multiplier")

  //Hook flow to sink and then run with a source
  multiplier.to(sink).runWith(source)

  override def akkaStreamsExample: Future[_] = for {
  //Connect a flow with a source and then run with a fold
    _ <- source.via(multiplier).runFold(0)(_ + _)
    //Hook source and sink into flow and run
    _ <- multiplier.runWith(source, sink)._2
  } yield ()

  runExample
}