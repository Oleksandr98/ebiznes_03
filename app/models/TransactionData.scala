package models

import play.api.libs.json.Json

import java.sql.Date

case class TransactionData(id: Long, modifyDate: Option[Date], createDate: Option[Date], status: String, value: Option[Double],
                           orderId: Option[Long], comment: Option[String], customerId: Long, locationId: Option[Long], tType: String)

case class WSTransactionData(value: Option[Double], orderId: Option[Long], comment: Option[String], customerId: Long, locationId: Option[Long])

case class WSUpdateTransactionData(value: Option[Double], orderId: Option[Long], comment: Option[String], locationId: Option[Long])

case class WSResponseTransactionData(transaction: TransactionData, customer: Option[CustomerData], location: Option[LocationData], order: Option[WSResponseOrderData])

object TransactionData {
  implicit val transactionFormat = Json.format[TransactionData]
}

object WSTransactionData {
  implicit val createTransactionFormat = Json.format[WSTransactionData]
}

object WSUpdateTransactionData {
  implicit val updateTransactionFormat = Json.format[WSUpdateTransactionData]
}

object WSResponseTransactionData {
  implicit val responseTransactionFormat = Json.format[WSResponseTransactionData]
}
