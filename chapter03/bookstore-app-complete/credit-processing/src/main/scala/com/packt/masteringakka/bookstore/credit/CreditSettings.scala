package com.packt.masteringakka.bookstore.credit

import akka.actor._
import com.typesafe.config.Config

/**
 * Settings extension for credit related settings
 */
class CreditSettingsImpl(conf:Config) extends Extension{
  val creditConfig = conf.getConfig("credit")
  val creditChargeUrl = creditConfig.getString("creditChargeUrl")
}
object CreditSettings extends ExtensionId[CreditSettingsImpl] with ExtensionIdProvider { 
  override def lookup = CreditSettings 
  override def createExtension(system: ExtendedActorSystem) =
    new CreditSettingsImpl(system.settings.config)
}