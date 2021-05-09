package models

import play.api.libs.json.Json

import java.sql.Date

case class ShoppingCartData(id: Long, createDate: Option[Date], modifyDate: Option[Date], removeDate: Option[Date], value: Double,
                            orderId: Option[Long], customerId: Long)

case class WSShoppingCartData(customerId: Long)

case class WSUpdateShoppingCartData(value: Option[Double], customerId: Option[Long], orderId: Option[Long])

case class WSResponseShoppingCartData(cart: ShoppingCartData, customer: Option[CustomerData], products: Option[Seq[CartProductsDataT]])

case class WSShoppingCartProductData(prdId: Long, quantity: Int)

case class WSShoppingCartProductRemData(prdId: Long)

case class CartProductsDataT(itemInfo: Option[ShoppingCartProductData], product: Option[ProductData])

object ShoppingCartData {
  implicit val sCartFormat = Json.format[ShoppingCartData]
}

object WSShoppingCartData {
  implicit val sCartCreateFormat = Json.format[WSShoppingCartData]
}

object WSUpdateShoppingCartData {
  implicit val sCartUpdateFormat = Json.format[WSUpdateShoppingCartData]
}

object WSShoppingCartProductData {
  implicit val sCartProductFormat = Json.format[WSShoppingCartProductData]
}

object WSShoppingCartProductRemData {
  implicit val sCartProductRemoveFormat = Json.format[WSShoppingCartProductRemData]
}

object CartProductsDataT {
  implicit val sCartPrdFormat = Json.format[CartProductsDataT]
}

object WSResponseShoppingCartData {
  implicit val sCartResFormat = Json.format[WSResponseShoppingCartData]
}

