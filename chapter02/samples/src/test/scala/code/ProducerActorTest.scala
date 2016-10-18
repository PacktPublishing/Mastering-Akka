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
import code.ActorQueue.Enqueue
import scala.concurrent.duration._

class ProducerActorTest extends TestSpec {
  "producer actor" should "produce 1000 enqueue messages upon receiving 'start'" in withProducerActorProps { (props, consumer) ⇒
    withTestProbe(props) { (send, tp, _) ⇒
      send("start")
      tp.expectNoMsg(100.millis)
      consumer.expectMsgAllOf((1 to 1000).map(Enqueue(_)): _*) // notice, all messages are received in order
      consumer.expectNoMsg(100.millis)
    }
  }

  it should "do nothing on receiving an unknown message" in withProducerActorProps { (props, consumer) ⇒
    withTestProbe(props) { (send, tp, _) ⇒
      send("unknown message")
      tp.expectNoMsg(100.millis)
      consumer.expectNoMsg(100.millis)
    }
  }

  /**
   * Initializes the ProducerActor's props with the actor ref of the test probe,
   * that will act as a 'consumer'. It will receive the messages that the producer
   * will send.
   */
  def withProducerActorProps(f: (Props, TestProbe) ⇒ Unit): Unit = {
    val tp = TestProbe()
    f(ProducerActor.props(tp.ref), tp)
  }
}
