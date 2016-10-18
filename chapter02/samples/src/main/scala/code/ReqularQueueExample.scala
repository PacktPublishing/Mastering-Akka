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

trait Queue {
  def enqueue(item: Int): Unit
  def dequeue(): Int
}

class UnsafeQueue extends Queue {
  var items: List[Int] = Nil

  def enqueue(item: Int) =
    items = items :+ item

  def dequeue(): Int = {
    if (items.isEmpty) 0
    else {
      val item = items.head
      items = items.drop(1)
      item
    }
  }
}

class SafeQueue extends Queue {
  var items: List[Int] = Nil
  def enqueue(item: Int) = synchronized {
    items = items :+ item
    notifyAll()
  }
  def dequeue(): Int = synchronized {
    while (items.isEmpty) {
      wait()
    }
    val item = items.head
    items = items.drop(1)
    item
  }
}

class Producer(queue: Queue) extends Thread {
  override def run = {
    for (i ← 1 to 1000) {
      queue.enqueue(1)
    }
  }
}

class Consumer(queue: Queue) extends Thread {
  override def run = {
    for (i ← 1 to 1000) {
      queue.dequeue()
    }
  }
}

object RegularSafeQueueExample extends App {
  val q = new SafeQueue
  for (i ← 1 to 10) {
    new Producer(q).start()
    new Consumer(q).start()
  }
}

object RegularUnsafeQueueExample extends App {
  val q = new UnsafeQueue
  for (i ← 1 to 10) {
    new Producer(q).start()
    new Consumer(q).start()
  }
}