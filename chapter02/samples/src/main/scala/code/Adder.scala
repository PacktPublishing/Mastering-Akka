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

object Adder {
  sealed trait Command
  case object Add extends Command
  case object Subtract extends Command
  case object GetValue extends Command
  def props = Props[Adder]
}

class Adder extends Actor {
  import Adder._
  def receive: Receive = amount(0)

  def amount(value: Long): Receive = LoggingReceive {
    case Add      ⇒ context.become(amount(value + 1))
    case Subtract ⇒ context.become(amount(value - 1))
    case GetValue ⇒ sender() ! value
  }
}
