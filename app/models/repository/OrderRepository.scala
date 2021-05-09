package models.repository

import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import javax.inject.{Inject, Singleton}
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrderRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, ordPrdRepo: OrderProductRepository,
                                prdRepo: ProductRepository, ctmRepo: CustomerRepository)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class OrdersTable(tag: Tag) extends Table[OrderData](tag, "ORDERS") {

    def id = column[Long]("ORD_ID", O.PrimaryKey, O.AutoInc)

    def modifyDate = column[Option[Date]]("ORD_MODIFY_DATE")

    def createDate = column[Option[Date]]("ORD_CREATE_DATE")

    def removeDate = column[Option[Date]]("ORD_REMOVE_DATE")

    def discount = column[Option[Double]]("ORD_DISCOUNT")

    def customerId = column[Option[Long]]("ORD_CTM_ID")

    private def customer = foreignKey("CUSTOMER_FK", customerId, customerData)(_.id)


    def * = (id, createDate, modifyDate, removeDate, discount, customerId) <> ((OrderData.apply _).tupled, OrderData.unapply)
  }

  import ctmRepo.CustomerTable
  import ordPrdRepo.OrderProductsTable
  import prdRepo.ProductTable

  private val orderData = TableQuery[OrdersTable]
  private val orderProductsData = TableQuery[OrderProductsTable]
  private val productData = TableQuery[ProductTable]
  private val customerData = TableQuery[CustomerTable]

  def create(ordData: WSOrderData): Future[Long] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (orderData.map(orderData => (orderData.createDate, orderData.modifyDate, orderData.removeDate, orderData.discount, orderData.customerId))
      returning orderData.map(_.id)
      ) += (currentDate, Option.empty, Option.empty, ordData.discount, ordData.customerId)
  }

  def getAll(): Future[immutable.Iterable[WSResponseOrderData]] = db.run {
    (for {
      ((orderProdData, ordData), prodData) <- orderData.filter(_.removeDate.column.isEmpty).joinLeft(orderProductsData).on(_.id === _.ordId).
        joinLeft(productData).on(_._2.map(_.prdId) === _.id)
    } yield (orderProdData, ordData, prodData)
      ).result.map(_.toList.groupBy(_._1).map(x => {
      var seq = Seq[OrderProductsDataT]()
      for (elem <- x._2) {
        seq :+= OrderProductsDataT(elem._2, elem._3)
      }
      WSResponseOrderData(x._1, seq)
    }))
  }

  def getByIdComplete(id: Long): Future[immutable.Iterable[WSResponseOrderData]] = db.run {
    (for {
      ((orderProdData, ordData), prodData) <- orderData.filter(x => x.id === id && x.removeDate.column.isEmpty).joinLeft(orderProductsData).on(_.id === _.ordId).
        joinLeft(productData).on(_._2.map(_.prdId) === _.id)
    } yield (orderProdData, ordData, prodData)
      ).result.map(_.toList.groupBy(_._1).map(x => {
      var seq = Seq[OrderProductsDataT]()
      for (elem <- x._2) {
        seq :+= OrderProductsDataT(elem._2, elem._3)
      }
      WSResponseOrderData(x._1, seq)
    }))
  }

  def getById(id: Long): Future[Option[OrderData]] = db.run {
    orderData.filter(o => o.id === id && o.removeDate.column.isEmpty).result.headOption
  }

  def removeById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    orderData.filter(o => o.id === id && o.removeDate.column.isEmpty).map(v => v.removeDate).update(currentDate)
  }

  def updateById(id: Long, ordData: WSUpdateOrderData): Future[Future[Future[Int]]] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "ORD_MODIFY_DATE = " + currentDate.get.getTime
    if (ordData.discount.isDefined) updateQuery += ", ORD_DISCOUNT = '" + ordData.discount.get + "'"
    getById(id).map(order => {
      if (order.isDefined && ordData.prdId.isDefined) {
        ordPrdRepo.create(ordData.prdId.get, id, ordData.quantity).map(_ => {
          db.run(sql"UPDATE ORDERS SET #$updateQuery WHERE ORD_ID = $id AND ORD_REMOVE_DATE IS NULL".asUpdate)
        })
      } else {
        Future.successful(db.run(sql"UPDATE ORDERS SET #$updateQuery WHERE ORD_ID = $id AND ORD_REMOVE_DATE IS NULL".asUpdate))
      }
    })
  }

}
