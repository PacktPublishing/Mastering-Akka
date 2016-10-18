package code

import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.NotUsed
import scala.concurrent.Future
import akka.Done
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Flow

object StreamBuilding extends AkkaStreamsApp {
  val source: Source[Int, NotUsed] = Source(1 to 5)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
  val dataflow: RunnableGraph[Future[Done]] =
    source
      .log("dataflow")
      .toMat(sink)(Keep.right)

  val sink2: Sink[Int, Future[Int]] = Sink.fold(0)(_+_)
  val dataflow2: RunnableGraph[Future[Int]] =
    source
      .log("dataflow2")
      .toMat(sink2)(Keep.right)

  val flow = Flow[Int].map(_*2).filter(_ % 2 == 0)
  val fut2 = source.log("flow3").via(flow).toMat(sink2)(Keep.right).run

  override def akkaStreamsExample: Future[_] = for {
    _ <- dataflow.run
    _ <- dataflow2.run
    _ <- fut2
  } yield ()

  runExample
}