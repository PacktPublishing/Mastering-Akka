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

import akka.actor.Props
import akka.testkit.TestProbe
import code.ActorQueue.Dequeue

import scala.concurrent.duration._

class ConsumerActorTest extends TestSpec {

  "consumer actor" should "send enqueue to the queue upon receiving 'start'" in withConsumerActorProps { (props, queue) ⇒
    withTestProbe(props) { (send, tp, _) ⇒
      send("start")
      tp.expectNoMsg(100.millis)
      queue.expectMsg(Dequeue)
      queue.expectNoMsg(100.millis)
    }
  }

  it should "send a dequeue message to the queue, upon receiving 1000 integers" in withConsumerActorProps { (props, queue) ⇒
    withTestProbe(props) { (send, tp, consumerActor) ⇒
      (1 to 1000).foreach(send(_))
      tp.expectNoMsg(100.millis)
      queue.expectMsgAllOf((1 to 999).map(_ ⇒ Dequeue): _*)
      queue.expectNoMsg(100.millis)
      tp watch consumerActor // the test probe must watch the actor to receive actor life cycle messages
      tp.expectTerminated(consumerActor) // when the consumer actor has been terminated, the 'Terminated' message will be expected
    }
  }

  /**
   * Initializes the ConsumerActor's props with the actor ref of the test probe,
   * that will act as the 'queue'. It will receive the messages that the consumer
   * will send.
   */
  def withConsumerActorProps(f: (Props, TestProbe) ⇒ Unit): Unit = {
    val tp = TestProbe()
    f(ConsumerActor.props(tp.ref), tp)
  }
}
