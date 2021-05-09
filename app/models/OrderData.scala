package models

import play.api.libs.json.Json

import java.sql.Date

case class OrderData(id: Long, createDate: Option[Date], modifyDate: Option[Date], removeDate: Option[Date], discount: Option[Double], customerId: Option[Long])
case class WSOrderData(discount: Option[Double], customerId: Option[Long])
case class WSUpdateOrderData(discount: Option[Double], prdId: Option[Long], quantity: Int)
case class WSResponseOrderData(order: OrderData, orderProducts: Seq[OrderProductsDataT])
case class OrderProductsDataT(itemInfo: Option[OrderProductsData], product: Option[ProductData])

object OrderData {
  implicit val orderFormat = Json.format[OrderData]
}

object WSOrderData {
  implicit val createFormat = Json.format[WSOrderData]
}

object WSUpdateOrderData {
  implicit val updateFormat = Json.format[WSUpdateOrderData]
}

object OrderProductsDataT {
  implicit val responseFormat = Json.format[OrderProductsDataT]
}

object WSResponseOrderData {
  implicit val responseFormat = Json.format[WSResponseOrderData]
}


