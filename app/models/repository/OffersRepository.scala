package models.repository

import models.{OfferData, WSOfferData, WSUpdateOfferData}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OffersRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  private class OffersTable(tag: Tag) extends Table[OfferData](tag, "OFFERS") {

    def id = column[Long]("OFR_ID", O.PrimaryKey, O.AutoInc)

    def code = column[String]("OFR_CODE")

    def name = column[String]("OFR_NAME")

    def description = column[String]("OFR_DESCRIPTION")

    def createDate = column[Option[Date]]("OFR_CREATE_DATE")

    def modifyDate = column[Option[Date]]("OFR_MODIFY_DATE")

    def removeDate = column[Option[Date]]("OFR_REMOVE_DATE")
    def startDate = column[Date]("OFR_START_DATE")
    def endDate = column[Option[Date]]("OFR_END_DATE")

    def * = (id, name, description, code, removeDate, createDate, modifyDate, startDate, endDate) <> ((OfferData.apply _).tupled, OfferData.unapply)
  }

  private val offerData = TableQuery[OffersTable]

  def create(ofrData: WSOfferData): Future[Long] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (offerData.map(l => (l.name, l.description, l.code, l.removeDate, l.createDate, l.modifyDate, l.startDate, l.endDate))
      returning offerData.map(_.id)
      ) += (ofrData.name, ofrData.description, ofrData.code, Option.empty, currentDate, Option.empty, ofrData.startDate, ofrData.endDate)
  }

  def getAll(): Future[Seq[OfferData]] = db.run {
    offerData.filter(o => o.removeDate.column.isEmpty).result
  }

  def getById(id: Long): Future[Option[OfferData]] = db.run {
    offerData.filter(o => o.id === id && o.removeDate.column.isEmpty).result.headOption
  }

  def removeById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    offerData.filter(o => o.id === id && o.removeDate.column.isEmpty).map(v => v.removeDate).update(currentDate)
  }

  def updateById(id: Long, ofrData: WSUpdateOfferData): Future[Int] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "OFR_MODIFY_DATE = " + currentDate.get.getTime
    if (ofrData.code.isDefined) updateQuery += ", OFR_CODE = '" + ofrData.code.get + "'"
    if (ofrData.name.isDefined) updateQuery += ", OFR_NAME = '" + ofrData.name.get + "'"
    if (ofrData.description.isDefined) updateQuery += ", OFR_DESCRIPTION = '" + ofrData.description.get + "'"
    if (ofrData.startDate.isDefined) updateQuery += ", OFR_START_DATE = '" + ofrData.startDate.get.getTime + "'"
    if (ofrData.endDate.isDefined) updateQuery += ", OFR_END_DATE = '" + ofrData.endDate.get.getTime + "'"
    db.run(sql"UPDATE OFFERS SET #$updateQuery WHERE OFR_ID = $id AND OFR_REMOVE_DATE IS NULL".asUpdate)
  }

}
