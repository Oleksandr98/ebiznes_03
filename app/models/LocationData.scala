package models

import play.api.libs.json.Json

import java.sql.Date

case class LocationData(id: Long, code: String, locType: String, description: String, createDate: Option[Date], modifyData: Option[Date], removeDate: Option[Date])

case class WSLocationData(code: String, locType: String, description: String)

case class WSUpdateLocationData(code: Option[String], locType: Option[String], description: Option[String])

object LocationData {
  implicit val locationFormat = Json.format[LocationData]
}

object WSLocationData {
  implicit val locationCreateFormat = Json.format[WSLocationData]
}

object WSUpdateLocationData {
  implicit val locationUpdateFormat = Json.format[WSUpdateLocationData]
}
