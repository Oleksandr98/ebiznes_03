package models.repository

import models.{ProductData, WSUpdateProductData}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProductRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, catRepo: CategoryRepository)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class ProductTable(tag: Tag) extends Table[ProductData](tag, "PRODUCTS") {

    def id = column[Long]("PRD_ID", O.PrimaryKey, O.AutoInc)

    def name = column[String]("PRD_NAME")

    def code = column[String]("PRD_CODE")

    def description = column[String]("PRD_DESCRIPTION")

    def removeDate = column[Option[Date]]("PRD_REMOVE_DATE")

    def modifyDate = column[Option[Date]]("PRD_MODIFY_DATE")

    def createDate = column[Option[Date]]("PRD_CREATE_DATE")

    def value = column[Double]("PRD_VALUE")

    def categoryId = column[Long]("PRD_CAT_ID")

    private def category = foreignKey("CATEGORY_FK", categoryId, categoryData)(_.id)

    def * = (id, name, code, description, removeDate, modifyDate, createDate, value, categoryId) <> ((ProductData.apply _).tupled, ProductData.unapply)
  }

  import catRepo.CategoryTable

  private val productData = TableQuery[ProductTable]

  private val categoryData = TableQuery[CategoryTable]

  def create(name: String, code: String, description: String, categoryId: Long, value: Double): Future[Long] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (productData.map(p => (p.name, p.code, p.description, p.removeDate, p.modifyDate, p.createDate, p.value, p.categoryId))
      returning productData.map(_.id)
      ) += (name, code, description, Option.empty, Option.empty, currentDate, value, categoryId)
  }

  def getAll(): Future[Seq[ProductData]] = db.run {
    productData.filter(_.removeDate.column.isEmpty).result
  }

  def getById(id: Long): Future[Option[ProductData]] = db.run {
    productData.filter(p => p.id === id && p.removeDate.column.isEmpty).result.headOption
  }

  def removeById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    productData.filter(p => p.id === id && p.removeDate.column.isEmpty).map(v => v.removeDate).update(currentDate)
  }

  def updateById(id: Long, pData: WSUpdateProductData): Future[Int] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "PRD_MODIFY_DATE = " + currentDate.get.getTime
    if (pData.name.isDefined) updateQuery += ", PRD_NAME = '" + pData.name.get + "'"
    if (pData.code.isDefined) updateQuery += ", PRD_CODE = '" + pData.code.get + "'"
    if (pData.description.isDefined) updateQuery += ", PRD_DESCRIPTION = '" + pData.description.get + "'"
    if (pData.categoryId.isDefined) updateQuery += ", PRD_CAT_ID = " + pData.categoryId.get
    if (pData.value.isDefined) updateQuery += ", PRD_VALUE = " + pData.value.get
    db.run(sql"UPDATE PRODUCTS SET #$updateQuery WHERE PRD_ID = $id AND PRD_REMOVE_DATE IS NULL".asUpdate)
  }

}

