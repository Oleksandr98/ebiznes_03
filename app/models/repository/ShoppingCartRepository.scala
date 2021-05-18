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
class ShoppingCartRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, ordRepo: OrderRepository,
                                       cusRepo: CustomerRepository, shcPrdRepo: ShoppingCartProductsRepository,
                                       prdRepo: ProductRepository, ordProdRepo: OrderProductRepository,
                                       tranRepo: TransactionRepository, cardRepo: CardRepository)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import cusRepo.CustomerTable
  import dbConfig._
  import ordRepo.OrdersTable
  import prdRepo.ProductTable
  import profile.api._
  import shcPrdRepo.ShoppingCartProductsTable

  class ShoppingCartTable(tag: Tag) extends Table[ShoppingCartData](tag, "SHOPPING_CART") {

    def id = column[Long]("SHC_ID", O.PrimaryKey, O.AutoInc)

    def modifyDate = column[Option[Date]]("SHC_MODIFY_DATE")

    def createDate = column[Option[Date]]("SHC_CREATE_DATE")

    def removeDate = column[Option[Date]]("SHC_REMOVE_DATE")

    def value = column[Double]("SHC_VALUE")

    def orderId = column[Option[Long]]("SHC_ORD_ID")

    def customerId = column[Long]("SHC_CTM_ID")

    private def order = foreignKey("ORDER_FK", orderId, orderData)(_.id)

    private def customer = foreignKey("CUSTOMER_FK", customerId, customerData)(_.id)

    def * = (id, createDate, modifyDate, removeDate, value, orderId, customerId) <> ((ShoppingCartData.apply _).tupled, ShoppingCartData.unapply)
  }

  private val orderData = TableQuery[OrdersTable]
  private val customerData = TableQuery[CustomerTable]
  private val shoppingCartData = TableQuery[ShoppingCartTable]
  private val shoppingCartProductsData = TableQuery[ShoppingCartProductsTable]
  private val productData = TableQuery[ProductTable]


  def create(shData: WSShoppingCartData): Future[Long] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    (shoppingCartData.map(sData => (sData.createDate, sData.modifyDate, sData.removeDate, sData.value, sData.orderId, sData.customerId))
      returning shoppingCartData.map(_.id)
      ) += (currentDate, Option.empty, Option.empty, 0, Option.empty, shData.customerId)
  }

  def placeOrder(shId: Long, ctmId: Long, d: WSShoppingCartOrderData): Future[Int] = {
    val id: Long = Await.result(ordRepo.create(new WSOrderData(d.discountValue, Option.apply(ctmId))), Duration.Inf)
    Await.result(db.run(shoppingCartProductsData.filter(spData => spData.shcId === shId).result),Duration.Inf).map(
      prdData => ordProdRepo.create(prdData.prdId, id, prdData.quantity)
    )
    val res = Await.result(db.run(shoppingCartData.filter(x => x.id === shId).result.head), Duration.Inf)
    var newValue: Double = 0
    if (d.discountValue.isDefined) {
      newValue = res.value*d.discountValue.get
      newValue = newValue/100
      newValue = res.value - newValue
    } else {
      newValue = res.value
    }
    if (d.cardId.isDefined) {
      cardRepo.updatePointsBalance(d.cardId.get, (newValue/10).toInt)
    }
    tranRepo.create(new WSTransactionData(Option.apply(newValue), Option.apply(id), Option.apply("Transaction order: " + id), ctmId, Option.empty), TransactionTypes.Sale.toString)

    removeByIdFinal(shId)
  }

  def getAll(): Future[immutable.Iterable[WSResponseShoppingCartData]] = db.run {
    (for {
      (((cart, crtPrd), prd), customer) <- shoppingCartData.filter(_.removeDate.column.isEmpty).joinLeft(shoppingCartProductsData).on(_.id === _.shcId)
        .joinLeft(productData).on(_._2.map(_.prdId) === _.id).joinLeft(customerData).on(_._1._1.customerId === _.id)
    } yield (cart, customer, crtPrd, prd)
      ).result.map(_.toList.groupBy(_._1).map(x => {
      var seq = Seq[CartProductsDataT]()
      for (elem <- x._2) {
        seq :+= CartProductsDataT(elem._3, elem._4)
      }
      WSResponseShoppingCartData(x._1, x._2.head._2, Option.apply(seq))
    }))
  }

  def getById(id: Long): Future[immutable.Iterable[WSResponseShoppingCartData]] = db.run {
    (for {
      (((cart, crtPrd), prd), customer) <- shoppingCartData.filter(sc => sc.id === id && sc.removeDate.column.isEmpty).joinLeft(shoppingCartProductsData).on(_.id === _.shcId)
        .joinLeft(productData).on(_._2.map(_.prdId) === _.id).joinLeft(customerData).on(_._1._1.customerId === _.id)
    } yield (cart, customer, crtPrd, prd)
      ).result.map(_.toList.groupBy(_._1).map(x => {
      var seq = Seq[CartProductsDataT]()
      for (elem <- x._2) {
        seq :+= CartProductsDataT(elem._3, elem._4)
      }
      WSResponseShoppingCartData(x._1, x._2.head._2, Option.apply(seq))
    }))
  }

  def removeById(id: Long): Future[Int] = db.run {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    shoppingCartData.filter(sc => sc.id === id && sc.removeDate.column.isEmpty).map(v => v.removeDate).update(currentDate)
  }

  def removeByIdFinal(id: Long): Future[Int] = db.run {
    shoppingCartData.filter(sc => sc.id === id).delete
  }

  def addProduct(shcId: Long, prdId: Long, quantity: Int): Future[Future[Int]] = {
    db.run(productData.filter(_.id === prdId).result.headOption).map {
      case Some(prd) =>
        val prdValues: Seq[ShoppingCartProductData] = Await.result(db.run(shoppingCartProductsData.filter(sc => sc.shcId === shcId).result), Duration.Inf)
        val currentPrdValue = prd.value * quantity
        var totalValue = currentPrdValue
        var existingId: Long = -1
        for (elem <- prdValues) {
          totalValue = totalValue + elem.value
          if (elem.prdId == prd.id) {
            existingId = prd.id
          }
        }
        val currentDate = Option.apply(new Date(new java.util.Date().getTime))
        db.run(shoppingCartData.filter(sc => sc.id === shcId && sc.removeDate.column.isEmpty)
          .map(v => (v.modifyDate, v.value)).update(currentDate, totalValue)).map {
          case 1 =>
            if (existingId == -1) {
              shcPrdRepo.create(prdId, shcId, quantity, currentPrdValue)
            } else {
              shcPrdRepo.increment(prdId, shcId, quantity, currentPrdValue)
            }
            1
          case _ => 0
        }
      case None => Future.successful(0)
    }
  }

  def removeProduct(shcId: Long, prdId: Long): Future[Future[Int]] = {
    db.run(productData.filter(_.id === prdId).result.headOption).map {
      case Some(prd) =>
        val prdValues: Seq[ShoppingCartProductData] = Await.result(db.run(shoppingCartProductsData.filter(sc => sc.shcId === shcId).result), Duration.Inf)
        if (prdValues.nonEmpty) {
          var totalValue: Double = 0
          for (elem <- prdValues) {
            if (elem.prdId == prdId) {

            } else {
              totalValue = totalValue + elem.value
            }

          }
          val currentDate = Option.apply(new Date(new java.util.Date().getTime))
          db.run(shoppingCartData.filter(sc => sc.id === shcId && sc.removeDate.column.isEmpty).map(v => (v.modifyDate, v.value)).update(currentDate, totalValue)).map {
            case 1 =>
              shcPrdRepo.removeProductById(prdId, shcId)
              1
            case _ => 0
          }
        } else {
          Future.successful(0)
        }
      case None => Future.successful(0)
    }
  }

  def updateById(id: Long, sData: WSUpdateShoppingCartData): Future[Int] = {
    val currentDate = Option.apply(new Date(new java.util.Date().getTime))
    var updateQuery = "SHC_MODIFY_DATE = " + currentDate.get.getTime
    if (sData.value.isDefined) updateQuery += ", SHC_VALUE = '" + sData.value.get + "'"
    if (sData.orderId.isDefined) updateQuery += ", SHC_ORD_ID = '" + sData.orderId.get + "'"
    if (sData.customerId.isDefined) updateQuery += ", SHC_CTM_ID = '" + sData.customerId.get + "'"
    db.run(sql"UPDATE SHOPPING_CART SET #$updateQuery WHERE SHC_ID = $id".asUpdate)
  }

}
