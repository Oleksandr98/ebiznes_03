package models

import play.api.libs.json.Json

import java.sql.Date

case class OfferData(id: Long, name: String, description: String, code: String, removeDate: Option[Date], createDate: Option[Date], modifyDate: Option[Date], startDate: Date, endDate: Option[Date])

case class WSOfferData(code: String, name: String, description: String, startDate: Date, endDate: Option[Date])

case class WSUpdateOfferData(code: Option[String], name: Option[String], description: Option[String], startDate: Option[Date], endDate: Option[Date])

object OfferData {
  implicit val categoryFormat = Json.format[OfferData]
}

object WSOfferData {
  implicit val wsCategoryFormat = Json.format[WSOfferData]
}

object WSUpdateOfferData {
  implicit val wsCategoryFormat = Json.format[WSUpdateOfferData]
}
