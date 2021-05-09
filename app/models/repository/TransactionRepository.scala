package models.repository

import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import javax.inject.{Inject, Singleton}
import scala.collection.immutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class TransactionRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, ordRepo: OrderRepository,
                                      ordPrdRepo: OrderProductRepository, prdRepo: ProductRepository, cusRepo: CustomerRepository,
                                      locRepo: LocationRepository)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import cusRepo.CustomerTable
  import dbConfig._
  import locRepo.LocationTable
  import ordPrdRepo.OrderProductsTable
  import ordRepo.OrdersTable
  import prdRepo.ProductTable
  import profile.api._

  class TransactionsTable(tag: Tag) extends Table[TransactionData](tag, "TRANSACTIONS") {

    def id = column[Long]("TRA_ID", O.PrimaryKey, O.AutoInc)

    def modifyDate = column[Option[Date]]("TRA_MODIFY_DATE")

    def createDate = column[Option[Date]]("TRA_CREATE_DATE")

    def status = column[String]("TRA_STATUS")

    def value = column[Option[Double]]("TRA_VALUE")

    def orderId = column[Option[Long]]("TRA_ORD_ID")

    def comment = column[Option[String]]("TRA_COMMENT")

    def customerId = column[Long]("TRA_CTM_ID")

    def locationId = column[Option[Long]]("TRA_LCN_ID")

    def tType = column[String]("TRA_TYPE")

    private def order = foreignKey("ORDER_FK", orderId, TableQuery[OrdersTable])(_.id)

    private def customer = foreignKey("CUSTOMER_FK", customerId, TableQuery[CustomerTable])(_.id)

    private def location = foreignKey("LOCATION_FK", customerId, TableQuery[LocationTable])(_.id)

    def * = (id, modifyDate, createDate, status, value, orderId, comment, customerId, locationId, tType) <> ((TransactionData.apply _).tupled, TransactionData.unapply)
  }

  private val orderData = TableQuery[OrdersTable]
  private val customerData = TableQuery[CustomerTable]
  private val locationData = TableQuery[LocationTable]
  private val transactionData = TableQuery[TransactionsTable]


  def create(tranData: WSTransactionData, tType: String): Future[Long] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (transactionData.map(tData => (tData.modifyDate, tData.createDate, tData.status, tData.value, tData.orderId, tData.comment, tData.customerId, tData.locationId, tData.tType))
      returning transactionData.map(_.id)
      ) += (Option.empty, currentDate, TransactionStatuses.Complete.toString, tranData.value, tranData.orderId, tranData.comment, tranData.customerId, tranData.locationId, tType)
  }

  def getAll(): Future[immutable.Iterable[WSResponseTransactionData]] = db.run {
    (for {
      (((transaction, customer), location), order) <- transactionData.joinLeft(customerData).on(_.customerId === _.id).
        joinLeft(locationData).on(_._1.locationId === _.id).joinLeft(orderData).on(_._1._1.orderId === _.id)
    } yield (transaction, customer, location, order)
      ).result.map(_.toList.groupBy(_._1).map(x => {
      if (x._2.head._4.isDefined) {
        val data = Await.result(ordRepo.getByIdComplete(x._2.head._4.map(_.id).get), Duration.Inf).head
        WSResponseTransactionData(x._1, x._2.head._2, x._2.head._3, Option.apply(data))
      } else {
        WSResponseTransactionData(x._1, x._2.head._2, x._2.head._3, Option.empty)
      }
    }))
  }

  def getById(id: Long): Future[immutable.Iterable[WSResponseTransactionData]] = db.run {
    (for {
      (((transaction, customer), location), order) <- transactionData.filter(_.id === id).joinLeft(customerData).on(_.customerId === _.id).
        joinLeft(locationData).on(_._1.locationId === _.id).joinLeft(orderData).on(_._1._1.orderId === _.id)
    } yield (transaction, customer, location, order)
      ).result.map(_.toList.groupBy(_._1).map(x => {
      if (x._2.head._4.isDefined) {
        val data = Await.result(ordRepo.getByIdComplete(x._2.head._4.map(_.id).get), Duration.Inf).head
        WSResponseTransactionData(x._1, x._2.head._2, x._2.head._3, Option.apply(data))
      } else {
        WSResponseTransactionData(x._1, x._2.head._2, x._2.head._3, Option.empty)
      }
    }))
  }

  def reverseById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    transactionData.filter(o => o.id === id).map(v => (v.modifyDate, v.status)).update(currentDate, TransactionStatuses.Reversed.toString)
  }


  def updateById(id: Long, tData: WSUpdateTransactionData): Future[Int] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "TRA_MODIFY_DATE = " + currentDate.get.getTime
    if (tData.value.isDefined) updateQuery += ", TRA_VALUE = '" + tData.value.get + "'"
    if (tData.orderId.isDefined) updateQuery += ", TRA_ORD_ID = '" + tData.orderId.get + "'"
    if (tData.comment.isDefined) updateQuery += ", TRA_COMMENT = '" + tData.comment.get + "'"
    if (tData.locationId.isDefined) updateQuery += ", TRA_LCN_ID = '" + tData.locationId.get + "'"
    db.run(sql"UPDATE TRANSACTIONS SET #$updateQuery WHERE TRA_ID = $id".asUpdate)
  }

}
