package code

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl._
import code.EdgeServices._

import scala.concurrent.Future

object EventPartialGraphExample extends AkkaStreamsApp with EdgeServices {

  val eventsFlow = Flow.fromGraph(GraphDSL.create() { 
    implicit builder: GraphDSL.Builder[NotUsed] =>
    
    import GraphDSL.Implicits._
    val weather = 
      Flow[Event].mapAsync(4)(e => fetchWeatherInfo(e.date))
    val imageInfo = 
      Flow[Event].mapAsync(4)(e => fetchImageInfo(e.imageUrl))      
        
    val bcast = builder.add(Broadcast[Event](3))
    val zip = builder.add(ZipWith[Event,WeatherData,ImageInfo,Event]{(e, w, i) => 
      e.copy(weather = Some(w), imageInfo = Some(i))      
    })
    
    bcast ~> zip.in0 
    bcast ~> weather ~> zip.in1 
    bcast ~> imageInfo ~> zip.in2 
    FlowShape(bcast.in, zip.out)
  })

  override def akkaStreamsExample: Future[_] =
    otherEventsSource
      .via(eventsFlow)
      .take(4000)
      .runWith(otherEventsSink)

  runExample
}