package models

import play.api.libs.json._

import java.sql.Date

case class ProductData(id: Long, name: String, code: String, description: String, removeDate: Option[Date], modifyDate: Option[Date], createDate: Option[Date],
                       value: Double, categoryId: Long)

case class WSProductData(name: String, code: String, description: String, value: Double, categoryId: Long)

case class WSUpdateProductData(name: Option[String], code: Option[String], description: Option[String], value: Option[Double], categoryId: Option[Long])

object ProductData {
  implicit val productWrites = Json.format[ProductData]
}

object WSProductData {
  implicit val productWrites = Json.format[WSProductData]
}

object WSUpdateProductData {
  implicit val productWrites = Json.format[WSUpdateProductData]
}
