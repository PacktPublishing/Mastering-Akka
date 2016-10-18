package code

import java.nio.file.FileSystems

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, RunnableGraph}
import akka.util.ByteString

import scala.concurrent.Future

object BlueprintExample extends AkkaStreamsApp {
  val file = this.getClass.getClassLoader.getResource("current_inventory.csv")
  val inPath = FileSystems.getDefault.getPath(file.getPath)
  val outPath = FileSystems.getDefault.getPath("no_inventory.csv")
  val fileSource = FileIO.fromPath(inPath)
  val fileSink = FileIO.toPath(outPath)

  val csvHandler: Flow[String, List[String], NotUsed] =
    Flow[String]
      .drop(1)
      .map(_.split(",").toList)
      .log("csvHandler")

  val lowInventoryFlow: RunnableGraph[Future[IOResult]] =
    fileSource
      .via(Framing.delimiter(ByteString("\n"), Integer.MAX_VALUE))
      .map(_.utf8String)
      .log("Before CSV Handler")
      .via(csvHandler)
      .filter(list => list(2).toInt == 0)
      .log("After filter")
      .map { list =>
        ByteString(list.mkString(",")) ++ ByteString("\n")
      }.toMat(fileSink)(Keep.right)

  override def akkaStreamsExample: Future[_] =
    lowInventoryFlow.run()

  runExample
}