package com.packt.masteringakka.bookstore.common

import java.util.Date
import akka.actor.ActorSystem
import com.typesafe.config.Config

/**
 * Base dao to use for other daos in the bookstore app
 */
trait BookstoreDao{
  import slick.driver.PostgresDriver.api._
  def db = PostgresDb.db  

  /**
   * Defines some helpers to use in daos
   */
  object DaoHelpers{
    
    /**
     * Adds a method to easily convert from util.Date to sql.Date 
     */
    implicit class EnhancedDate(date:Date){
      
      /**
       * Converts from the date suplied in the constructor into a sql.Date
       * @return a sql.Date 
       */
      def toSqlDate = new java.sql.Date(date.getTime) 
    }
  }  
  
  /**
   * Gets a select statement to use to select the last id val for a serial id field in Postgres
   * @param table The name of the table to get the last id from
   * @return a DBIOAction used to select the last id val
   */
  def lastIdSelect(table:String) = sql"select currval('#${table}_id_seq')".as[Int]
}

object PostgresDb{
  import slick.driver.PostgresDriver.backend._
  private[common] var db:Database = _
  
  def init(conf:Config):Unit = {
    db = Database.forConfig("psqldb", conf)
  }
}