package models

import play.api.libs.json.Json

import java.sql.Date


case class CustomerData(id: Long, name: String, surname: String, status: String, createDate: Option[Date],
                        modifyDate: Option[Date], closureDate: Option[Date], password: String, login: String, birthDate: Date)

case class WSCustomerData(name: String, surname: String, password: String, login: String, birthDate: Date)

case class WSUpdateCustomerData(name: Option[String], surname: Option[String], password: Option[String], login: Option[String],
                                birthDate: Option[Date])

object CustomerData {
  implicit val customerFormat = Json.format[CustomerData]
}

object WSCustomerData {
  implicit val customerCreateFormat = Json.format[WSCustomerData]
}

object WSUpdateCustomerData {
  implicit val customerUpdateFormat = Json.format[WSUpdateCustomerData]
}
