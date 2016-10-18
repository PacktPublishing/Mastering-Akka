package code

import akka.actor._
import scala.io.Source
import com.typesafe.config.ConfigFactory
import akka.util.Timeout

object WordCountWorker{
  case class CountWords(words:Seq[String])
  case class WordCounts(counts:Seq[(String,Int)])
  
  def props = Props[WordCountWorker]
}

class WordCountWorker extends Actor with ActorLogging{
  import WordCountWorker._
  
  def receive = {
    case CountWords(words) =>      
      val counts = 
        words.
          groupBy(identity).
          mapValues(_.size)
      log.info("Received a request and responding to {}", sender.path)
      sender() ! WordCounts(counts.toSeq)
  }
}

object WordCountMaster{
  case class CountWordsInResource(fileName:String)
  def props = Props[WordCountMaster]
}

class WordCountMaster extends Actor with ActorLogging{
  import WordCountMaster._
  
  val workerA = context.actorOf(WordCountWorker.props, "workerA")
  val workerB = context.actorOf(WordCountWorker.props, "workerB")
  
  def receive = startingReceive
  
  def startingReceive:Receive = {
    case CountWordsInResource(name) =>
      log.info("Received request to count words in {}", name)
      val in = getClass.getClassLoader().getResourceAsStream(name)
      val lines = Source.fromInputStream(in).getLines
      var expected = 0
      lines.foreach{ line =>            
        expected += 1
        val worker = if (expected % 2 == 0) workerA else workerB
        worker ! WordCountWorker.CountWords(line.split(" ").toList)
      }
      log.info("Done sending all requests, switching to wait for responses")
      context.become(waitingForCounts(sender(), expected, Seq.empty))
  }
  
  def waitingForCounts(caller:ActorRef, remaining:Int, counts:Seq[(String,Int)]):Receive = {
    case WordCountWorker.WordCounts(inCounts) =>
      val newCounts = 
        (counts ++ inCounts).
          groupBy(_._1).
          mapValues(_.map(_._2).sum).
          toSeq
      val newRemain = remaining - 1
      if (newRemain == 0){
        log.info("Counting words is complete, responding")
        caller ! WordCountWorker.WordCounts(newCounts)
        context.stop(self)
      }
      else{
        context.become(waitingForCounts(caller, newRemain, newCounts))
      }
  }
}

object WorkerNode extends App with RemotingConfig{
  val port = args(0).toInt
  val cfg = remotingConfig(port)
  val system = ActorSystem("WorkerSystem", ConfigFactory.parseString(cfg)
    .withFallback(ConfigFactory.defaultApplication()))
}

object WordCountApp extends App with RemotingConfig{
  import WordCountWorker._
  import WordCountMaster._
  
  val config = ConfigFactory.parseString(remotingConfig(2552))
    .withFallback(ConfigFactory.defaultApplication())
  val deployConfig = ConfigFactory.parseString("""
    akka {
      actor {
        deployment {          
          /wordCountMaster/workerA {
            remote = "akka.tcp://WorkerSystem@127.0.0.1:2553"
          }
          /wordCountMaster/workerB {
            remote = "akka.tcp://WorkerSystem@127.0.0.1:2554"
          }      
        }
      }
    }      
  """).withFallback(ConfigFactory.defaultApplication())
  val system = ActorSystem("MasterSystem", config.withFallback(deployConfig))
  val master = system.actorOf(WordCountMaster.props, "wordCountMaster")
  
  import akka.pattern.ask
  import concurrent.duration._
  import system.dispatcher
  implicit val timeout = Timeout(10 seconds)
  val fut = (master ? WordCountMaster.CountWordsInResource("declaration.txt")).mapTo[WordCounts]
  fut.
    andThen{
      case util.Success(counts) =>
        println("Done, top 10 words are:")        
        counts.counts.
          toList.
          sortBy(_._2)(Ordering.Int.reverse).
          take(10).
          foreach{
            case (word, count) =>
              println(s"$word=$count")
          }
      
      case util.Failure(ex) =>
        ex.printStackTrace()
    }.
    andThen{
      case tr =>
        system.terminate      
    }
}

