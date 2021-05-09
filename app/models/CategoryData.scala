package models

import play.api.libs.json.Json

import java.sql.Date

case class CategoryData(id: Long, code: String, name: String, removeDate: Option[Date], createDate: Option[Date])

case class WSCategoryData(code: String, name: String)

case class WSUpdateCategoryData(code: Option[String], name: Option[String])

object CategoryData {
  implicit val categoryFormat = Json.format[CategoryData]
}

object WSCategoryData {
  implicit val wsCategoryFormat = Json.format[WSCategoryData]
}

object WSUpdateCategoryData {
  implicit val wsCategoryFormat = Json.format[WSUpdateCategoryData]
}
