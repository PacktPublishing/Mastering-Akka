package com.packt.masteringakka.bookstore.user

import com.packt.masteringakka.bookstore.common.Bootstrap
import akka.actor.ActorSystem

/**
 * Boot class for booting up the user sub domain
 */
class UserBoot extends Bootstrap{
  def bootup(system:ActorSystem) = {
    import system.dispatcher
    
    val userManager = system.actorOf(UserManager.props, UserManager.Name)
    List(new UserEndpoint(userManager))
  }
}