package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common.Server

object Main {
  def main(args:Array[String]):Unit = {
    new Server(new OrderBoot(), "sales-order-processing")
  }
}