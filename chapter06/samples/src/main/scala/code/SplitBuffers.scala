package code

import akka.stream.Attributes
import akka.stream.scaladsl.Flow

import scala.concurrent.Future

object SplitBuffers extends AkkaStreamsApp {

  val separateMapStage = 
    Flow[Int].
      map(_*2).
      async.
      withAttributes(Attributes.inputBuffer(initial = 1, max = 1))
   
  val otherMapStage = 
    Flow[Int].
      map(_/2).
      async
      
  val totalFlow = separateMapStage.via(otherMapStage)

  override def akkaStreamsExample: Future[_] =
    Future.successful(())

  runExample
}