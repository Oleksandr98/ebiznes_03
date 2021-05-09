package models

import play.api.libs.json.Json

import java.sql.Date

case class CouponData(id: Long, number: String, status: String, createDate: Option[Date], modifyDate: Option[Date], removeDate: Option[Date], customerId: Option[Long])

case class WSCouponData(number: String, customerId: Option[Long])

case class WSUpdateCouponData(number: Option[String], status: Option[String], customerId: Option[Long])

object CouponData {
  implicit val couponFormat = Json.format[CouponData]
}

object WSCouponData {
  implicit val couponCreateFormat = Json.format[WSCouponData]
}

object WSUpdateCouponData {
  implicit val couponUpdateFormat = Json.format[WSUpdateCouponData]
}
