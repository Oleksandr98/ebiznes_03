package models

import play.api.libs.json.Json

import java.sql.Date

case class OrderProductsData(id: Long, createDate: Option[Date], removeDate: Option[Date], prdId: Long, ordId: Long, quantity: Int)

object OrderProductsData {
  implicit val orderPrdFormat = Json.format[OrderProductsData]
}