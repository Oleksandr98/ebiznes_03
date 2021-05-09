package models

import play.api.libs.json.Json

import java.sql.Date

case class CardData(id: Long, number: String, status: String, createDate: Option[Date], modifyDate: Option[Date], customerId: Option[Long])

case class WSCardData(number: String, customerId: Option[Long])

case class WSUpdateCardData(number: Option[String], status: Option[String], customerId: Option[Long])

object CardData {
  implicit val cardFormat = Json.format[CardData]
}

object WSUpdateCardData {
  implicit val cardUpdateFormat = Json.format[WSUpdateCardData]
}

object WSCardData {
  implicit val cardCreateFormat = Json.format[WSCardData]
}
