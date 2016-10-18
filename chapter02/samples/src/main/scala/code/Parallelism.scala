/*
 * Copyright 2016 Packt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package code

import akka.actor._
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.util.Timeout

import scala.concurrent.duration._

object WorkMaster {
  sealed trait Command
  case object StartProcessing extends Command
  case object DoWorkerWork extends Command
  final case class IterationCount(count: Long) extends Command
  def props(workerCount: Int) = Props(classOf[WorkMaster], workerCount)
}

class WorkMaster(workerCount: Int) extends Actor {
  import WorkMaster._

  val workers = context.actorOf(
    ParallelismWorker.props.withRouter(
      RoundRobinPool(workerCount)
    ), "workerRouter"
  )
  def receive = waitingForRequest

  def waitingForRequest: Receive = {
    case StartProcessing ⇒
      val requestCount = 50000
      for (i ← 1 to requestCount) {
        workers ! DoWorkerWork
      }
      context.become(collectingResults(requestCount, sender()))
  }

  def collectingResults(remaining: Int, caller: ActorRef, iterations: Long = 0): Receive = {
    case IterationCount(count) ⇒
      val newRemaining = remaining - 1
      val newIterations = count + iterations
      if (newRemaining == 0) {
        caller ! IterationCount(newIterations)
        context.stop(self)
        context.system.terminate

      } else {
        context.become(collectingResults(newRemaining, caller, newIterations))
      }
  }
}

object ParallelismWorker {
  def props() = Props[ParallelismWorker]
}

class ParallelismWorker extends Actor {
  import WorkMaster._

  def receive = {
    case DoWorkerWork ⇒
      var totalIterations = 0L
      var count = 10000000
      while (count > 0) {
        totalIterations += 1
        count -= 1
      }

      sender() ! IterationCount(totalIterations)
  }
}

object ParallelismExample extends App {
  implicit val timeout = Timeout(60.seconds)
  val workerCount = args.headOption.getOrElse("8").toInt
  println(s"Using $workerCount worker instances")

  val system = ActorSystem("parallelism")
  import system.dispatcher
  sys.addShutdownHook(system.terminate)

  val master = system.actorOf(WorkMaster.props(workerCount), "master")
  val start = System.currentTimeMillis()
  (master ? WorkMaster.StartProcessing).
    mapTo[WorkMaster.IterationCount].
    flatMap { iterations ⇒
      val time = System.currentTimeMillis() - start
      println(s"total time was: $time ms")
      println(s"total iterations was: ${iterations.count}")
      system.terminate()
    }.
    recover {
      case t: Throwable ⇒
        t.printStackTrace()
        system.terminate()
    }
}
