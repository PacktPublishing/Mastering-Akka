package code

import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import scala.io.Source
import akka.routing.FromConfig
import concurrent.duration._

trait ClusterConfig{
  def clusterConfig(port:Int, role:String) = s"""
    akka {
      actor {
        provider = "akka.cluster.ClusterActorRefProvider"
      }
      remote {       
        netty.tcp {
          hostname = "127.0.0.1"
          port = $port
        }
      }
      cluster {
        roles = ["$role"]
        auto-down-unreachable-after = 10s
        seed-nodes = [
          "akka.tcp://WordCountSystem@127.0.0.1:2553",
          "akka.tcp://WordCountSystem@127.0.0.1:2554"
        ]
      }          
    }
  """
}

object ClusterWorkerNode extends App with ClusterConfig{
  val port = args(0).toInt
  val cfg = clusterConfig(port, "worker")
  val system = ActorSystem("WordCountSystem", ConfigFactory.parseString(cfg)
    .withFallback(ConfigFactory.defaultApplication()))
}

object ClusterStateListener{
  def props = Props[ClusterStateListener]
}
class ClusterStateListener extends Actor with ActorLogging{
  val cluster = Cluster(context.system)
 
  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  } 
  
  override def postStop(): Unit = cluster.unsubscribe(self)
  
  def receive = {
    case MemberUp(member) =>
      log.info("New member in cluster: {}", member.address)
      
    case UnreachableMember(member) =>
      log.info("Member is unreachable: {}", member)
      
    case MemberRemoved(member, previousStatus) =>
      log.info("Member has been removed: {} after {}",
        member.address, previousStatus)
        
    case _: MemberEvent => 
      //Nothing to do here for other MemberEvent types    
  }
}

object ClusterWordCountMaster{
  case class CountWordsInResource(fileName:String, totalTimes:Int, waitBetween:Int)
  def props = Props[ClusterWordCountMaster]
}

class ClusterWordCountMaster extends Actor with ActorLogging{
  import ClusterWordCountMaster._
  
  val workerPool = context.actorOf(Props[WordCountWorker].withRouter(FromConfig()), "workerPool")
  
  def receive = startingReceive()
  
  def startingReceive(executionCount:Int = 0):Receive = {
    case req:CountWordsInResource if executionCount >= req.totalTimes =>
      log.info("Done with execution, completed {} total runs", executionCount)
      context stop self
      
    case req:CountWordsInResource =>
      log.info("Received request to count words in {}, exec count is {}", req.fileName, executionCount)
      val in = getClass.getClassLoader().getResourceAsStream(req.fileName)
      val lines = Source.fromInputStream(in).getLines
      var expected = 0
      lines.foreach{line =>            
        expected += 1
        workerPool ! WordCountWorker.CountWords(line.split(" ").toList)
      }
      log.info("Done sending all requests, switching to wait for responses")
      context.become(waitingForCounts(sender(), expected, Seq.empty, req, executionCount))
  }
  
  def waitingForCounts(caller:ActorRef, remaining:Int, counts:Seq[(String,Int)], req:CountWordsInResource, execCount:Int):Receive = {
    case WordCountWorker.WordCounts(inCounts) =>
      val newCounts = 
        (counts ++ inCounts).
          groupBy(_._1).
          mapValues(_.map(_._2).sum).
          toSeq
      val newRemain = remaining - 1
      if (newRemain == 0){
        log.info("Counting words is complete, pausing and then checking to start cycle again")
        context.system.scheduler.scheduleOnce(req.waitBetween.seconds, self, req)(context.dispatcher)
        context.become(startingReceive(execCount + 1))
      }
      else{
        context.become(waitingForCounts(caller, newRemain, newCounts, req, execCount))
      }
  }
}

object ClusterWordCountApp extends App with ClusterConfig{
  import WordCountWorker._
  import WordCountMaster._
  
  val executions = args(0).toInt
  val pauseSeconds = args(1).toInt
  
  val config = ConfigFactory.parseString(clusterConfig(2552, "master"))
  val deployConfig = ConfigFactory.parseString("""
    akka {
      actor {
        deployment {          
          /wordCountMaster/workerPool {
            router = round-robin-pool
            cluster {
              enabled = on
              max-nr-of-instances-per-node = 2
              allow-local-routees = off
              use-role = worker
            }             
          }      
        }
      }
    }      
  """)
  val system = ActorSystem("WordCountSystem", config.withFallback(deployConfig)
    .withFallback(ConfigFactory.defaultApplication()))
  val listener = system.actorOf(ClusterStateListener.props)
  
  val master = system.actorOf(ClusterWordCountMaster.props, "wordCountMaster")
  Thread.sleep(10000)
  master ! ClusterWordCountMaster.CountWordsInResource("declaration.txt", executions, pauseSeconds )
}