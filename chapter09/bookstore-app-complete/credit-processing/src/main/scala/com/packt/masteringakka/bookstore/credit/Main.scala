package com.packt.masteringakka.bookstore.credit

import com.packt.masteringakka.bookstore.common.Server

object Main {
  def main(args:Array[String]):Unit = {
    new Server(new CreditBoot(), "credit-processing")
  }
}