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
import akka.event.LoggingReceive

object ActorQueue {
  sealed trait Command
  final case class Enqueue(item: Int) extends Command
  case object Dequeue extends Command
  def props = Props[ActorQueue]
}

class ActorQueue extends Actor with Stash {
  import ActorQueue._

  def receive = emptyReceive

  def emptyReceive: Receive = LoggingReceive {
    case Enqueue(item) ⇒
      context.become(nonEmptyReceive(List(item)))
      unstashAll

    case Dequeue ⇒
      stash
  }

  def nonEmptyReceive(items: List[Int]): Receive = LoggingReceive {
    case Enqueue(item) ⇒
      context.become(nonEmptyReceive(items :+ item))

    case Dequeue ⇒
      sender() ! items.head
      context.become(determineReceive(items))
  }

  def determineReceive(items: List[Int]): Receive =
    if (items.tail.isEmpty) emptyReceive else nonEmptyReceive(items.tail)
}

object ProducerActor {
  def props(queue: ActorRef) = Props(classOf[ProducerActor], queue)
}

class ProducerActor(queue: ActorRef) extends Actor {
  def receive = {
    case "start" ⇒
      for (i ← 1 to 1000) queue ! ActorQueue.Enqueue(i)
  }
}

object ConsumerActor {
  def props(queue: ActorRef) = Props(classOf[ConsumerActor], queue)
}

class ConsumerActor(queue: ActorRef) extends Actor with ActorLogging {

  def receive = consumerReceive(1000)

  def consumerReceive(remaining: Int): Receive = {
    case "start" ⇒
      queue ! ActorQueue.Dequeue

    case i: Int ⇒
      val newRemaining = remaining - 1
      if (newRemaining == 0) {
        log.info("Consumer {} is done consuming", self.path)
        context.stop(self)
      } else {
        queue ! ActorQueue.Dequeue
        context.become(consumerReceive(newRemaining))
      }
  }
}

object ActorQueueExample extends App {
  val system = ActorSystem()
  val queue = system.actorOf(ActorQueue.props)

  val pairs =
    for (i ← 1 to 10) yield {
      val producer = system.actorOf(ProducerActor.props(queue))
      val consumer = system.actorOf(ConsumerActor.props(queue))
      (consumer, producer)
    }

  val reaper = system.actorOf(ShutdownReaper.props)
  pairs.foreach {
    case (consumer, producer) ⇒
      reaper ! consumer
      consumer ! "start"
      producer ! "start"
  }
}

object ShutdownReaper {
  def props = Props[ShutdownReaper]
}

class ShutdownReaper extends Actor {
  def receive = shutdownReceive(0)
  def shutdownReceive(watching: Int): Receive = {
    case ref: ActorRef ⇒
      context.watch(ref)
      context.become(shutdownReceive(watching + 1))

    case t: Terminated if watching - 1 == 0 ⇒
      println("All consumers done, terminating actor system")
      terminate()

    case t: Terminated ⇒
      context.become(shutdownReceive(watching - 1))
  }

  def terminate(): Unit =
    context.system.terminate()
}
