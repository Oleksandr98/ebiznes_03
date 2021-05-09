package models.repository

import models.{CouponData, CouponStatuses, WSUpdateCouponData}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CouponsRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, ctmRepo: CustomerRepository)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class CouponsTable(tag: Tag) extends Table[CouponData](tag, "COUPONS") {

    def id = column[Long]("CPN_ID", O.PrimaryKey, O.AutoInc)

    def number = column[String]("CPN_NUM")

    def status = column[String]("CPN_STATUS")

    def createDate = column[Option[Date]]("CPN_CREATE_DATE")

    def modifyDate = column[Option[Date]]("CPN_MODIFY_DATE")

    def removeDate = column[Option[Date]]("CPN_REMOVE_DATE")

    def customerId = column[Option[Long]]("CPN_CTM_ID")

    private def customer = foreignKey("CUSTOMER_FK", customerId, customerData)(_.id)

    def * = (id, number, status, createDate, modifyDate, removeDate, customerId) <> ((CouponData.apply _).tupled, CouponData.unapply)
  }

  import ctmRepo.CustomerTable

  private val couponData = TableQuery[CouponsTable]
  private val customerData = TableQuery[CustomerTable]

  def create(number: String, cId: Option[Long]): Future[Long] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (couponData.map(couponData => (couponData.number, couponData.status, couponData.createDate, couponData.modifyDate, couponData.removeDate, couponData.customerId))
      returning couponData.map(_.id)
      ) += (number, CouponStatuses.New.toString, currentDate, Option.empty, Option.empty, cId)
  }

  def getAll(): Future[Seq[CouponData]] = db.run {
    couponData.filter(couponD => couponD.removeDate.column.isEmpty).result
  }

  def getById(id: Long): Future[Option[CouponData]] = db.run {
    couponData.filter(couponD => couponD.id === id && couponD.removeDate.column.isEmpty).result.headOption
  }

  def removeById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    couponData.filter(couponD => couponD.id === id && couponD.removeDate.column.isEmpty).map(v => v.removeDate).update(currentDate)
  }

  def updateById(id: Long, couponData: WSUpdateCouponData): Future[Int] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "CPN_MODIFY_DATE = " + currentDate.get.getTime
    if (couponData.number.isDefined) updateQuery += ", CPN_NUM = '" + couponData.number.get + "'"
    if (couponData.status.isDefined) updateQuery += ", CPN_STATUS = '" + couponData.status.get + "'"
    if (couponData.customerId.isDefined) updateQuery += ", CPN_CTM_ID = '" + couponData.customerId.get + "'"
    db.run(sql"UPDATE COUPONS SET #$updateQuery WHERE CPN_ID = $id AND CPN_REMOVE_DATE IS NULL".asUpdate)
  }


}
