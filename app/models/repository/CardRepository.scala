package models.repository

import models.{CardData, CardStatuses, WSUpdateCardData}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CardRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, ctmRepo: CustomerRepository)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class CardTable(tag: Tag) extends Table[CardData](tag, "CARDS") {

    def id = column[Long]("CRD_ID", O.PrimaryKey, O.AutoInc)

    def number = column[String]("CRD_NUM")

    def status = column[String]("CRD_STATUS")

    def points = column[Int]("CRD_POINTS")

    def createDate = column[Option[Date]]("CRD_CREATE_DATE")

    def modifyDate = column[Option[Date]]("CRD_MODIFY_DATE")

    def customerId = column[Option[Long]]("CRD_CTM_ID")

    private def customer = foreignKey("CUSTOMER_FK", customerId, customerData)(_.id)

    def * = (id, number, status, points, createDate, modifyDate, customerId) <> ((CardData.apply _).tupled, CardData.unapply)
  }

  import ctmRepo.CustomerTable

  private val cardData = TableQuery[CardTable]
  private val customerData = TableQuery[CustomerTable]

  def create(number: String, cId: Option[Long]): Future[Long] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (cardData.map(cD => (cD.number, cD.status, cD.createDate, cD.modifyDate, cD.customerId))
      returning cardData.map(_.id)
      ) += (number, CardStatuses.New.toString, currentDate, Option.empty, cId)
  }

  def getAll(): Future[Seq[CardData]] = db.run {
    cardData.result
  }

  def getById(id: Long): Future[Option[CardData]] = db.run {
    cardData.filter(cD => cD.id === id).result.headOption
  }

  def closeById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    cardData.filter(cD => cD.id === id).map(v => (v.status, v.modifyDate)).update(CardStatuses.Closed.toString, currentDate)
  }

  def updateById(id: Long, cData: WSUpdateCardData): Future[Int] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "CRD_MODIFY_DATE = " + currentDate.get.getTime
    if (cData.number.isDefined) updateQuery += ", CRD_NUM = '" + cData.number.get + "'"
    if (cData.status.isDefined) updateQuery += ", CRD_STATUS = '" + cData.status.get + "'"
    if (cData.customerId.isDefined) updateQuery += ", CRD_CTM_ID = '" + cData.customerId.get + "'"
    db.run(sql"UPDATE CARDS SET #$updateQuery WHERE CRD_ID = $id".asUpdate)
  }

  def updatePointsBalance(id: Long, value: Int): Future[Int] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "CRD_MODIFY_DATE = " + currentDate.get.getTime
    updateQuery += ", CRD_POINTS = CRD_POINTS + '" + value + "'"
    db.run(sql"UPDATE CARDS SET #$updateQuery WHERE CRD_ID = $id".asUpdate)
  }

}
