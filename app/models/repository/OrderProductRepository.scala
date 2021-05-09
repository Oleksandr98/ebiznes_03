package models.repository

import models.OrderProductsData
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrderProductRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class OrderProductsTable(tag: Tag) extends Table[OrderProductsData](tag, "ORDERS_PRODUCTS") {

    def id = column[Long]("OP_ID", O.PrimaryKey, O.AutoInc)

    def createDate = column[Option[Date]]("OP_CREATE_DATE")

    def removeDate = column[Option[Date]]("OP_REMOVE_DATE")

    def prdId = column[Long]("OP_PRD_ID")

    def ordId = column[Long]("OP_ORD_ID")

    def quantity = column[Int]("OP_PRD_QUANTITY")

    def * = (id, createDate, removeDate, prdId, ordId, quantity) <> ((OrderProductsData.apply _).tupled, OrderProductsData.unapply)
  }

  private val ordPrdData = TableQuery[OrderProductsTable]

  def create(prdId: Long, ordId: Long, quantity: Int): Future[Long] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (ordPrdData.map(orPrData => (orPrData.createDate, orPrData.removeDate, orPrData.prdId, orPrData.ordId, orPrData.quantity))
      returning ordPrdData.map(_.id)
      ) += (currentDate, Option.empty, prdId, ordId, quantity)
  }

  def getById(id: Long): Future[Option[OrderProductsData]] = db.run {
    ordPrdData.filter(orPrData => orPrData.id === id && orPrData.removeDate.column.isEmpty).result.headOption
  }

  def removeProductById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    ordPrdData.filter(orPrData => orPrData.id === id && orPrData.removeDate.column.isEmpty).map(v => v.removeDate).update(currentDate)
  }

}

