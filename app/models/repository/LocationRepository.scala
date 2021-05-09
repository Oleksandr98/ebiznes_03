package models.repository

import models.{LocationData, LocationTypes, WSLocationData, WSUpdateLocationData}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LocationRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class LocationTable(tag: Tag) extends Table[LocationData](tag, "LOCATIONS") {

    def id = column[Long]("LCN_ID", O.PrimaryKey, O.AutoInc)

    def code = column[String]("LCN_CODE")

    def locType = column[String]("LCN_TYPE")

    def description = column[String]("LCN_DESCRIPTION")

    def createDate = column[Option[Date]]("LCN_CREATE_DATE")

    def modifyDate = column[Option[Date]]("LCN_MODIFY_DATE")

    def removeDate = column[Option[Date]]("LCN_REMOVE_DATE")

    def * = (id, code, locType, description, createDate, modifyDate, removeDate) <> ((LocationData.apply _).tupled, LocationData.unapply)
  }

  private val locationData = TableQuery[LocationTable]

  def create(locData: WSLocationData): Future[Long] = db.run {
    validateType(locData.locType)
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (locationData.map(l => (l.code, l.locType, l.description, l.createDate, l.modifyDate, l.removeDate))
      returning locationData.map(_.id)
      ) += (locData.code, locData.locType, locData.description, currentDate, Option.empty, Option.empty)
  }

  def getAll(): Future[Seq[LocationData]] = db.run {
    locationData.filter(l => l.removeDate.column.isEmpty).result
  }

  def getById(id: Long): Future[Option[LocationData]] = db.run {
    locationData.filter(l => l.id === id && l.removeDate.column.isEmpty).result.headOption
  }

  def removeById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    locationData.filter(l => l.id === id && l.removeDate.column.isEmpty).map(v => v.removeDate).update(currentDate)
  }

  def updateById(id: Long, locData: WSUpdateLocationData): Future[Int] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "LCN_MODIFY_DATE = " + currentDate.get.getTime
    if (locData.code.isDefined) updateQuery += ", LCN_CODE = '" + locData.code.get + "'"
    if (locData.locType.isDefined) {
      validateType(locData.locType.get)
      updateQuery += ", LCN_TYPE = '" + locData.locType.get + "'"
    }
    if (locData.description.isDefined) updateQuery += ", LCN_DESCRIPTION = '" + locData.description.get + "'"
    db.run(sql"UPDATE LOCATIONS SET #$updateQuery WHERE LCN_ID = $id AND LCN_REMOVE_DATE IS NULL".asUpdate)
  }

  def validateType(locType: String): Unit = {
    if (!LocationTypes.isLocationType(locType)) {
      throw new NoSuchElementException("Enum LocationTypes does not contain value: " + locType)
    }
  }

}
