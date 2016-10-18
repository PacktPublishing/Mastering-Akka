package com.packt.masteringakka.bookstore.order

import akka.testkit._
import akka.actor._
import com.packt.masteringakka.bookstore.domain.credit.CreditCardInfo
import java.util.Date
import com.packt.masteringakka.bookstore.domain.user.FindUserById
import com.packt.masteringakka.bookstore.domain.user.BookstoreUser
import com.packt.masteringakka.bookstore.common.FullResult
import com.packt.masteringakka.bookstore.domain.book.FindBook
import com.packt.masteringakka.bookstore.common.FullResult
import com.packt.masteringakka.bookstore.domain.book.Book
import com.packt.masteringakka.bookstore.domain.credit.ChargeCreditCard
import com.packt.masteringakka.bookstore.common.FullResult
import com.packt.masteringakka.bookstore.domain.credit.CreditCardTransaction
import com.packt.masteringakka.bookstore.domain.credit.CreditTransactionStatus
import scala.concurrent.Future
import com.packt.masteringakka.bookstore.order.SalesOrderProcessor.CreditHandlerName
import com.packt.masteringakka.bookstore.order.SalesOrderProcessor.UserManagerName
import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._

class SalesOrderProcessorUnitSpec extends FlatSpec with Matchers with BeforeAndAfterAll with MockitoSugar{  
  import SalesOrderProcessor._  
  implicit val system = ActorSystem()
  
  class scoping extends TestKit(system) with ImplicitSender{
    val userMgr = TestProbe(UserManagerName)
    val bookMgr = TestProbe(SalesOrderManager.BookMgrName)
    val creditHandler = TestProbe(CreditHandlerName )
    val namesMap = 
      Map(
        UserManagerName -> userMgr.ref.path.name,
        SalesOrderManager.BookMgrName -> bookMgr.ref.path.name,
        CreditHandlerName -> creditHandler.ref.path.name
      )
     
    val nowDate = new Date
    val mockDao = mock[SalesOrderProcessorDao]
    val orderProcessor = TestActorRef(new SalesOrderProcessor{
      override val dao = mockDao
      override def newDate = nowDate
      override def lookup(name:String) = 
        context.actorSelection(s"akka://default/system/${namesMap.getOrElse(name, "")}")
    })
  } 
   
  "A request to create a new sales order" should 
    """write a new order to the db and respond with 
      that new order when everything succeeds""" in new scoping {

      val lineItem = LineItemRequest(2, 1)
      val cardInfo = CreditCardInfo("Chris Baxter", "Visa",
        "1234567890", new Date)
      val request = CreateOrder(1, List(lineItem), cardInfo)
      
      val expectedLineItem = SalesOrderLineItem(0, 0, lineItem.bookId,
        1, 19.99, nowDate, nowDate)
      val expectedOrder = SalesOrder(0, request.userId, 99, 
        SalesOrderStatus.InProgress, 19.99, List(expectedLineItem),
        nowDate, nowDate)
      val finalOrder = expectedOrder.copy(id = 987)
      
      when(mockDao.createSalesOrder(expectedOrder)).
        thenReturn(Future.successful(finalOrder))
      
      orderProcessor ! request
      
      userMgr.expectMsg(FindUserById(request.userId))
      userMgr.reply(FullResult(BookstoreUser(request.userId, 
        "Chris", "Baxter", "chris@masteringakka.com", 
        new Date, new Date)))
        
      bookMgr.expectMsg(FindBook(lineItem.bookId))
      bookMgr.reply(FullResult(Book(lineItem.bookId, 
        "20000 Leagues Under the Sea", "Jules Verne", List("fiction"), 19.99,
        10, new Date, new Date)))
        
      creditHandler.expectMsg(ChargeCreditCard(cardInfo, 19.99))
      creditHandler.reply(FullResult(CreditCardTransaction(99, cardInfo,
        19.99, CreditTransactionStatus.Approved, Some("abc123"),
        new Date, new Date)))   
        
      expectMsg(FullResult(finalOrder))  
     
  }
  
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  } 
}