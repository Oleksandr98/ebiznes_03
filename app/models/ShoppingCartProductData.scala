package models

import play.api.libs.json.Json

case class ShoppingCartProductData(id: Long, prdId: Long, orderId: Long, quantity: Int, value: Double)

object ShoppingCartProductData {
  implicit val shcPrdFormat = Json.format[ShoppingCartProductData]
}
