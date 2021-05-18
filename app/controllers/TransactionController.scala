package controllers

import models.repository.{CustomerRepository, LocationRepository, OrderRepository, TransactionRepository}
import models._
import play.api.data.Forms._
import play.api.data.format.Formats.doubleFormat
import play.api.data.{Form, FormError}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

@Singleton
class TransactionController @Inject()(val tranRepo: TransactionRepository, ctmRepo: CustomerRepository, lcnRepo: LocationRepository,
                                      ordRepo: OrderRepository, cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = Json.obj(
      "key" -> Json.toJson(o.key),
      "message" -> Json.toJson(o.message)
    )
  }

  val transactionAddForm: Form[WSTransactionData] = Form {
    mapping(
      "value" -> optional(of[Double]),
      "orderId" -> optional(longNumber),
      "comment" -> optional(nonEmptyText),
      "customerId" -> longNumber,
      "locationId" -> optional(longNumber)
    )(WSTransactionData.apply)(WSTransactionData.unapply)
  }

  val transactionUpdateForm: Form[WSUpdateTransactionData] = Form {
    mapping(
      "value" -> optional(of[Double]),
      "orderId" -> optional(longNumber),
      "comment" -> optional(nonEmptyText),
      "locationId" -> optional(longNumber)
    )(WSUpdateTransactionData.apply)(WSUpdateTransactionData.unapply)
  }

  def getTransactions() = Action.async { implicit request: Request[AnyContent] =>
    for {
      list <- tranRepo.getAll()
    } yield {
      val json = Json.toJson(list)
      Ok(json)
    }
  }

  def getTransactionsForm() = Action.async { implicit request: Request[AnyContent] =>
    val list = tranRepo.getAll()
    list.map(transaction => Ok(views.html.transactions.transactions(transaction)))
  }

  def getTransactionForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    tranRepo.getById(id).map {
      case transaction if transaction.nonEmpty => Ok(views.html.transactions.transaction(transaction.head))
      case _ => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def getTransaction(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    tranRepo.getById(id).map {
      case t if t.nonEmpty => Ok(Json.toJson(t))
      case _ => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def modifyTransactionJSON(id: Long) = Action.async { implicit request =>
    transactionUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { t =>
      tranRepo.updateById(id, t).map {
        case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("updated: " + id)))
        case 0 => BadRequest(Json.obj(
          "status" -> "Error",
          "message" -> s"Not found item by id: $id",
        ))
      }
    })
  }

  def modifyTransaction(id: Long) = Action.async { implicit request =>
    transactionUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { t =>
      tranRepo.updateById(id, t).map {
        case 1 => Redirect("/transactions/" + id + "/form")
        case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
      }
    })
  }

  def modifyTransactionForm(id: Long) = Action.async { implicit request =>
    var location: Seq[LocationData] = Seq[LocationData]()
    val locations = lcnRepo.getAll().onComplete {
      case Success(value) => location = value
      case Failure(_) => print("fail")
    }

    var order: Iterable[WSResponseOrderData] = Seq[WSResponseOrderData]()
    val orders = ordRepo.getAll().onComplete {
      case Success(value) => order = value
      case Failure(_) => print("fail")
    }

    val tran = tranRepo.getById(id)
    tran.map {
      case x if x.nonEmpty =>
        val transactionForm = transactionUpdateForm.fill(WSUpdateTransactionData(x.head.transaction.value, x.head.transaction.orderId,
          x.head.transaction.comment, x.head.transaction.locationId))
        Ok(views.html.transactions.transactionupdate(transactionForm, location, order, x.head.transaction.id))
      case _ => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def createSaleTransactionJSON() = Action.async { implicit request =>
    transactionAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { tData =>
      tranRepo.create(tData, TransactionTypes.Sale.toString).map(id =>
        Ok(Json.obj("status" -> "OK", "message" -> ("created " + id))))
    })
  }

  def createSaleTransaction() = Action.async { implicit request =>
    transactionAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { tData =>
      tranRepo.create(tData, TransactionTypes.Sale.toString).map(id =>
        Redirect("/transactions/" + id + "/form"))
    })
  }

  def createSaleTransactionForm() = Action.async { implicit request =>
    val customers = ctmRepo.getAll()
    customers.map(customer => {
      val locations = Await.result(lcnRepo.getAll(), Duration.Inf)
      val orders = Await.result(ordRepo.getAll(), Duration.Inf)
      Ok(views.html.transactions.transactionadd(transactionAddForm, customer, locations, orders))
    })
  }

  // transactions cannot be removed, can only be reversed
  def reverseTransaction(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    tranRepo.reverseById(id).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("reversed: " + id)))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def reverseTransactionForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    tranRepo.reverseById(id).map {
      case 1 => Redirect("/transactions/form").flashing("SUCCESS" -> s"transaction $id reversed")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

}
