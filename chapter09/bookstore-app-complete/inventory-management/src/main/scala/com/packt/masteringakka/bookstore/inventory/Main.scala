package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.Server

object Main {
  def main(args:Array[String]):Unit = {
    new Server(new InventoryBoot(), "inventory-management")
  }
}