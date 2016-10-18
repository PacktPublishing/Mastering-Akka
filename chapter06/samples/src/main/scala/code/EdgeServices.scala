package code

import java.util.Date

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import code.EdgeServices._

import scala.concurrent.Future
import scala.util.Random

object EdgeServices {
  case class WeatherData(temp: Int, rain: Boolean)
  case class ImageInfo(tags:List[String], colors:List[String])
  case class Event(eventType:String, date:Date, imageUrl:String,
                   weather:Option[WeatherData], imageInfo:Option[ImageInfo])
}

trait EdgeServices {
  def now: Date = new Date
  def randomInt: Int = Random.nextInt(100)
  def randomBoolean: Boolean = Random.nextBoolean()
  def getWeatherData: WeatherData = WeatherData(randomInt, randomBoolean)
  def weatherDataIterator: Iterator[WeatherData] = new Iterator[WeatherData] {
    override def hasNext: Boolean = true
    override def next(): WeatherData = getWeatherData
  }
  def getImageInfo: ImageInfo = ImageInfo(List("happy"), List("red", "yellow", "white"))
  def imageInfoIterator: Iterator[ImageInfo] = new Iterator[ImageInfo] {
    override def hasNext: Boolean = true
    override def next(): ImageInfo = getImageInfo
  }
  def getEvent: Event = Event("EventType", now, "http://packt.com/happy.gif", if(randomBoolean) Some(getWeatherData) else None, if(randomBoolean) Some(getImageInfo) else None)
  def eventIterator: Iterator[Event] = new Iterator[Event] {
    override def hasNext: Boolean = true
    override def next(): Event = getEvent
  }

  def otherEventsSource:Source[Event,NotUsed] = Source.cycle(() => eventIterator)
  def otherEventsSink:Sink[Event,Future[Done]] = Flow[Event].log("other-events-sink").toMat(Sink.ignore)(Keep.right)

  def s3EventSource: Source[Event, NotUsed] = Source.cycle(() => eventIterator)
  def fetchWeatherInfo(date:Date)(implicit mat: Materializer): Future[WeatherData] =
    Source.cycle(() => weatherDataIterator).take(1).runWith(Sink.head)

  def fetchImageInfo(imageUrl:String)(implicit mat: Materializer): Future[ImageInfo] =
    Source.cycle(() => imageInfoIterator).take(1).runWith(Sink.head)
  def redshiftSink: Sink[Event, Future[Done]] =
    Flow[Event]
      .take(200)
      .log("saving to redShift")
      .toMat(Sink.ignore)(Keep.right)
}
