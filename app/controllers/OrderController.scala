package controllers

import models.repository.{CustomerRepository, OrderRepository, ProductRepository}
import models.{ProductData, WSOrderData, WSUpdateOrderData}
import play.api.data.Forms._
import play.api.data.format.Formats.doubleFormat
import play.api.data.{Form, FormError}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class OrderController @Inject()(val ordRepo: OrderRepository, ctmRepo: CustomerRepository,
                                prdRepo: ProductRepository, cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = Json.obj(
      "key" -> Json.toJson(o.key),
      "message" -> Json.toJson(o.message)
    )
  }

  val orderAddForm: Form[WSOrderData] = Form {
    mapping(
      "discount" -> optional(of[Double]),
      "customerId" -> optional(longNumber),
    )(WSOrderData.apply)(WSOrderData.unapply)
  }

  val orderUpdateForm: Form[WSUpdateOrderData] = Form {
    mapping(
      "discount" -> optional(of[Double]),
      "prdId" -> optional(longNumber),
      "quantity" -> number
    )(WSUpdateOrderData.apply)(WSUpdateOrderData.unapply)
  }

  def getOrders() = Action.async { implicit request: Request[AnyContent] =>
    for {
      list <- ordRepo.getAll()
    } yield {
      val json = Json.toJson(list)
      Ok(json)
    }
  }

  def getOrdersForm() = Action.async { implicit request =>
    val list = ordRepo.getAll()
    list.flatMap(order => order.head.order.customerId match {
      case Some(id) => ctmRepo.getById(id).map(x => Ok(views.html.orders.orders(order, x)))
      case None => Future.successful(Ok(views.html.orders.orders(order, Option.empty)))
    })
  }

  def getOrder(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ordRepo.getByIdComplete(id).map {
      case o if o.nonEmpty => Ok(Json.toJson(o))
      case _ => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def getOrderForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ordRepo.getByIdComplete(id).flatMap {
      case order if order.nonEmpty => order.head.order.customerId match {
        case Some(id) => ctmRepo.getById(id).map(x => Ok(views.html.orders.order(order.head, x)))
        case None => Future.successful(Ok(views.html.orders.order(order.head, Option.empty)))
      }
      case _ => Future.successful(BadRequest(views.html.businesserror(s"Not found item by id: $id")))
    }
  }

  def createOrderJSON() = Action.async { implicit request =>
    orderAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { orderData =>
      ordRepo.create(orderData).map(id =>
        Ok(Json.obj("status" -> "OK", "message" -> ("created: " + id))))
    })
  }

  def createOrder() = Action.async { implicit request =>
    orderAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { orderData =>
      ordRepo.create(orderData).map(id => Redirect("/orders/" + id + "/form"))
    })
  }

  def createOrderForm() = Action.async { implicit request: MessagesRequest[AnyContent] =>
    val customers = ctmRepo.getAll()
    customers.map(customer => Ok(views.html.orders.orderadd(orderAddForm, customer)))
  }

  // removes order
  def cancelOrder(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ordRepo.removeById(id).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("removed: " + id)))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def cancelOrderForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ordRepo.removeById(id).map {
      case 1 => Redirect("/orders/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def modifyOrderJSON(id: Long) = Action.async { implicit request =>
    orderUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { orderData =>
      ordRepo.updateById(id, orderData)
        .flatMap(result =>
          result.flatMap
          (test =>
            test.map {
              case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("updated: " + id)))
              case 0 => BadRequest(Json.obj(
                "status" -> "Error",
                "message" -> s"Not found item by id: $id",
              ))
            }
          )
        )
    })
  }

  def modifyOrder(id: Long) = Action.async { implicit request =>
    orderUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { orderData =>
      ordRepo.updateById(id, orderData)
        .flatMap(result =>
          result.flatMap
          (test =>
            test.map {
              case 1 => Redirect("/orders/" + id + "/form")
              case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
            }
          )
        )
    })
  }

  def modifyOrderForm(id: Long) = Action.async { implicit request =>
    var product: Seq[ProductData] = Seq[ProductData]()
    val products = prdRepo.getAll().onComplete {
      case Success(value) => product = value
      case Failure(_) => print("fail")
    }

    val ord = ordRepo.getById(id)
    ord.map {
      case Some(x) =>
        val prdForm = orderUpdateForm.fill(WSUpdateOrderData(x.discount, Option.empty, 1))
        Ok(views.html.orders.orderupdate(prdForm, product, x.id))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

}
