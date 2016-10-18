package code

import akka.stream.ClosedShape
import akka.stream.scaladsl._

import scala.concurrent.Future

object SimpleGraphOutputExample extends AkkaStreamsApp {

  val foldSink = Sink.fold[Int,Int](0)(_+_)
  val g = RunnableGraph.fromGraph(GraphDSL.create(foldSink) { 
    implicit builder => sink =>
    
    import GraphDSL.Implicits._
    val in = Source(1 to 5)
    val f1 = Flow[Int].map(_*2).log("f1")
    val f2 = Flow[Int].map(_ * 1).log("f2")
    val f3 = Flow[Int].map(_*2).log("f3")
    val f4 = Flow[Int].map(_+1).log("f4")
  
    val bcast = builder.add(Broadcast[Int](2))
    val merge = builder.add(Merge[Int](2))  
 
    in ~> f1 ~> bcast ~> f2 ~> merge  ~> f4 ~> sink
    bcast ~> f3 ~> merge
    ClosedShape
  })

  override def akkaStreamsExample: Future[_] =
    g.run

  runExample
}