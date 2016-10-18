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

import code.ActorQueue.{ Dequeue, Enqueue }

import scala.concurrent.duration._

class ActorQueueTest extends TestSpec {
  "ActorQueue" should "enqueue and dequeue an item" in withTestProbe(ActorQueue.props) { (send, tp, _) ⇒
    send(Enqueue(1))
    tp.expectNoMsg(100.millis)
    send(Dequeue)
    tp.expectMsg(1)
    tp.expectNoMsg(100.millis)
  }

  it should "stash a dequeue message when the actor is empty" in withTestProbe(ActorQueue.props) { (send, tp, _) ⇒
    send(Dequeue) // stash this message because the actor starts empty
    tp.expectNoMsg(100.millis)
    send(Enqueue(1))
    tp.expectMsg(1) // should immediately be dequeued because of the unstashed Dequeue message
    tp.expectNoMsg(100.millis)
  }
}