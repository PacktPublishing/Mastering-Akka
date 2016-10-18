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

import akka.actor.{ ActorRef, Props }
import akka.testkit.TestProbe

import scala.concurrent.duration._

class ShutdownReaperTest extends TestSpec {

  "reaper" should "shutdown the actor system when the actors are terminated" in withShutDownReaperTestProbe { (send, tp, actor1, actor2) ⇒
    send(actor1) // send two actor refs to the reaper to watch
    send(actor2)
    terminate(actor1) // terminate actor1
    tp.expectNoMsg(100.millis) // the reaper does nothing
    terminate(actor2) // terminate actor1
    tp.expectMsg("TERMINATE") // the reaper terminates the actor system
  }

  /**
   * Create a custom shutdown reaper that will send 'TERMINATE' to the test probe
   * by overriding the 'terminate' method of the reaper.
   */
  def withShutDownReaperTestProbe(f: (Any ⇒ Unit, TestProbe, ActorRef, ActorRef) ⇒ Unit): Unit = {
    val tp = TestProbe()
    val ref = system.actorOf(Props(new ShutdownReaper {
      override def terminate(): Unit = {
        tp.ref ! "TERMINATE"
      }
    }))
    val send = tp.send(ref, _: Any)
    try f(send, tp, ignoringActor, ignoringActor) finally terminate(ref)
  }
}
