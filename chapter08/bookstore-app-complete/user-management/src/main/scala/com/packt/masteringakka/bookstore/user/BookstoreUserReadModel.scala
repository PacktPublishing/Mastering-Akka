package com.packt.masteringakka.bookstore.user

import java.util.Date
import com.packt.masteringakka.bookstore.common.ReadModelObject
import com.packt.masteringakka.bookstore.common.ViewBuilder
import akka.actor.Props
import com.packt.masteringakka.bookstore.common.BookstoreActor
import com.packt.masteringakka.bookstore.common.ElasticsearchSupport
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer


trait BookstoreUserReadModel{
  def indexRoot = "user"
  def entityType = BookstoreUser.EntityType
}

object BookstoreUserViewBuilder{
  val Name = "user-view-builder"
  case class BookstoreUserRM(email:String, firstName:String, lastName:String, 
    createTs:Date, deleted:Boolean = false) extends ReadModelObject {
    def id = email
  }
  def props = Props[BookstoreUserViewBuilder]
}

class BookstoreUserViewBuilder extends ViewBuilder[BookstoreUserViewBuilder.BookstoreUserRM] with BookstoreUserReadModel with UserJsonProtocol{
  import BookstoreUser.Event._
  import ViewBuilder._
  import BookstoreUserViewBuilder._
  
  implicit val rmFormats = bookstoreUserRmFormat 
  def projectionId = Name
  def actionFor(id:String, env:EventEnvelope) = env.event match {
    case UserCreated(user) =>
      val rm = BookstoreUserRM(user.email, user.firstName, user.lastName, user.createTs, user.deleted)
      InsertAction(id, rm)
      
    case PersonalInfoUpdated(first, last) =>
      UpdateAction(id, List("firstName = fn", "lastName = ln"), Map("fn" -> first, "ln" -> last))
      
      
    case UserDeleted(email) =>
      UpdateAction(id, "deleted = true", Map.empty[String,Any])
  }
}

object BookstoreUserView{
  val Name = "bookstore-user-view"
  case class FindUsersByName(name:String)
  def props = Props[BookstoreUserView]
}

class BookstoreUserView extends BookstoreActor with ElasticsearchSupport with BookstoreUserReadModel with UserJsonProtocol{
  import BookstoreUserView._
  import BookstoreUserViewBuilder._
  import context.dispatcher
  implicit val mater = ActorMaterializer()
  
  def receive = {
    case FindUsersByName(name) =>
      val results = queryElasticsearch[BookstoreUserRM](s"firstName:$name OR lastName:$name")
      pipeResponse(results)      
  }
}