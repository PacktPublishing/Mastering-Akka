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

import code.Adder.{ Add, GetValue, Subtract }

import scala.concurrent.duration._

class AdderTest extends TestSpec {
  "Adder" should "start with 0" in withTestProbe(Adder.props) { (send, tp, _) ⇒
    send(GetValue)
    tp.expectMsg(0)
  }

  it should "Increment value with 1 on receive of Add" in withTestProbe(Adder.props) { (send, tp, _) ⇒
    send(Add)
    tp.expectNoMsg(100.millis)
    send(GetValue)
    tp.expectMsg(1)
  }

  it should "increment to 10" in withTestProbe(Adder.props) { (send, tp, _) ⇒
    (1 to 10).foreach(_ ⇒ send(Add))
    tp.expectNoMsg(100.millis)
    send(GetValue)
    tp.expectMsg(10)
  }

  it should "increment to 10 and decrement to 0" in withTestProbe(Adder.props) { (send, tp, _) ⇒
    (1 to 10).foreach(_ ⇒ send(Add))
    tp.expectNoMsg(100.millis)
    (1 to 10).foreach(_ ⇒ send(Subtract))
    tp.expectNoMsg(100.millis)
    send(GetValue)
    tp.expectMsg(0)
  }
}
