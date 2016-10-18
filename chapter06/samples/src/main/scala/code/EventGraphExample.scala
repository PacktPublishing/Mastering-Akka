package code

import akka.stream.ClosedShape
import akka.stream.scaladsl._
import code.EdgeServices._

import scala.concurrent.Future

object EventGraphExample extends AkkaStreamsApp with EdgeServices {

  val g = RunnableGraph.fromGraph(GraphDSL.create(redshiftSink) {
    implicit builder => sink =>

    import GraphDSL.Implicits._
    val eventsSource = s3EventSource
    val weather = Flow[Event].mapAsync(4)(e => fetchWeatherInfo(e.date)).log("weather")
    val imageInfo = Flow[Event].mapAsync(4)(e => fetchImageInfo(e.imageUrl)).log("image-info")

    val bcast = builder.add(Broadcast[Event](3))
    val zip = builder.add(ZipWith[Event,WeatherData,ImageInfo,Event]{(e, w, i) =>
      e.copy(weather = Some(w), imageInfo = Some(i))
    })

    eventsSource ~> bcast ~> zip.in0
                    bcast ~> weather ~> zip.in1
                    bcast ~> imageInfo ~> zip.in2
    zip.out ~> sink
    ClosedShape
  })

  override def akkaStreamsExample: Future[_] =
    g.run

  runExample
}