package com.packt.masteringakka.bookstore.user

import com.packt.masteringakka.bookstore.common.Server

object Main {
  def main(args:Array[String]):Unit = {
    new Server(new UserBoot(), "user-management")
  }
}