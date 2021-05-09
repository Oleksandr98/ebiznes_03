package models.repository

import models.{CategoryData, WSUpdateCategoryData}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CategoryRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class CategoryTable(tag: Tag) extends Table[CategoryData](tag, "CATEGORY") {

    def id = column[Long]("CAT_ID", O.PrimaryKey, O.AutoInc)

    def name = column[String]("CAT_NAME")

    def code = column[String]("CAT_CODE")

    def removeDate = column[Option[Date]]("CAT_REMOVE_DATE")

    def createDate = column[Option[Date]]("CAT_CREATE_DATE")

    def modifyDate = column[Option[Date]]("CAT_MODIFY_DATE")

    def * = (id, code, name, removeDate, createDate) <> ((CategoryData.apply _).tupled, CategoryData.unapply)
  }

  private val categoryData = TableQuery[CategoryTable]

  def create(code: String, name: String): Future[Long] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (categoryData.map(p => (p.code, p.name, p.removeDate, p.createDate))
      returning categoryData.map(_.id)
      ) += (code, name, Option.empty, currentDate)
  }

  def getAll(): Future[Seq[CategoryData]] = db.run {
    categoryData.filter(_.removeDate.column.isEmpty).result
  }

  def getById(id: Long): Future[Option[CategoryData]] = db.run {
    categoryData.filter(c => c.id === id && c.removeDate.column.isEmpty).result.headOption
  }

  def removeById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    categoryData.filter(c => c.id === id && c.removeDate.column.isEmpty).map(v => v.removeDate).update(currentDate)
  }

  def updateById(id: Long, cData: WSUpdateCategoryData): Future[Int] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "CAT_MODIFY_DATE = " + currentDate.get.getTime
    if (cData.name.isDefined) updateQuery += ", CAT_NAME = '" + cData.name.get + "'"
    if (cData.code.isDefined) updateQuery += ", CAT_CODE = '" + cData.code.get + "'"
    db.run(sql"UPDATE CATEGORY SET #$updateQuery WHERE CAT_ID = $id AND CAT_REMOVE_DATE IS NULL".asUpdate)
  }

}
