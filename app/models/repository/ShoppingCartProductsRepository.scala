package models.repository

import models.ShoppingCartProductData
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShoppingCartProductsRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class ShoppingCartProductsTable(tag: Tag) extends Table[ShoppingCartProductData](tag, "SHOPPING_CART_PRODUCTS") {

    def id = column[Long]("SP_ID", O.PrimaryKey, O.AutoInc)

    def prdId = column[Long]("SP_PRD_ID")

    def shcId = column[Long]("SP_SHC_ID")

    def quantity = column[Int]("SP_PRD_QUANTITY")

    def value = column[Double]("SP_VALUE")

    def * = (id, prdId, shcId, quantity, value) <> ((ShoppingCartProductData.apply _).tupled, ShoppingCartProductData.unapply)
  }

  private val shcPrdData = TableQuery[ShoppingCartProductsTable]

  def create(prdId: Long, shcId: Long, quantity: Int, value: Double): Future[Long] = db.run {
    (shcPrdData.map(shcPrData => (shcPrData.prdId, shcPrData.shcId, shcPrData.quantity, shcPrData.value))
      returning shcPrdData.map(_.id)
      ) += (prdId, shcId, quantity, value)
  }

  def increment(prdId: Long, shcId: Long, quantity: Int, value: Double): Future[Int] = {
    val updateQuery = "SP_PRD_QUANTITY = SP_PRD_QUANTITY + '" + quantity + "', SP_VALUE = SP_VALUE + '" + value + "'"
    db.run(sql"UPDATE SHOPPING_CART_PRODUCTS SET #$updateQuery WHERE SP_PRD_ID = $prdId AND SP_SHC_ID = $shcId".asUpdate)
  }

  def getById(id: Long): Future[Option[ShoppingCartProductData]] = db.run {
    shcPrdData.filter(shcPrData => shcPrData.id === id).result.headOption
  }

  def removeProductById(prdId: Long, shcId: Long): Future[Int] = db.run {
    shcPrdData.filter(shcPrData => shcPrData.prdId === prdId && shcPrData.shcId === shcId).delete
  }

}