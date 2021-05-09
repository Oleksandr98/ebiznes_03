package controllers

import models.repository.{CustomerRepository, OrderRepository, ProductRepository, ShoppingCartRepository}
import models.{CustomerData, ProductData, WSResponseOrderData, WSShoppingCartData, WSShoppingCartProductData, WSShoppingCartProductRemData, WSUpdateShoppingCartData}
import play.api.data.{Form, FormError}
import play.api.data.Forms.{longNumber, mapping, number, of, optional}
import play.api.data.format.Formats.doubleFormat
import play.api.libs.json.{JsError, JsValue, Json, Writes}
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class ShoppingCartController @Inject()(val shcRepo: ShoppingCartRepository, ctmRepo: CustomerRepository, ordRepo: OrderRepository,
                                       prodRepo: ProductRepository,
                                       cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = Json.obj(
      "key" -> Json.toJson(o.key),
      "message" -> Json.toJson(o.message)
    )
  }

  val shoppingCartAddForm: Form[WSShoppingCartData] = Form {
    mapping(
      "customerId" -> longNumber
    )(WSShoppingCartData.apply)(WSShoppingCartData.unapply)
  }

  val shoppingCartUpdateForm: Form[WSUpdateShoppingCartData] = Form {
    mapping(
      "value" -> optional(of[Double]),
      "customerId" -> optional(longNumber),
      "orderId" -> optional(longNumber)
    )(WSUpdateShoppingCartData.apply)(WSUpdateShoppingCartData.unapply)
  }

  val shoppingCartProdAddForm: Form[WSShoppingCartProductData] = Form {
    mapping(
      "prdId" -> longNumber,
      "quantity" -> number
    )(WSShoppingCartProductData.apply)(WSShoppingCartProductData.unapply)
  }

  def createCartJSON() = Action.async { implicit request =>
    shoppingCartAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { shcData =>
      shcRepo.create(shcData).map(id =>
        Ok(Json.obj("status" -> "OK", "message" -> ("created " + id))))
    })
  }

  def createCart() = Action.async { implicit request =>
    shoppingCartAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { shcData =>
      shcRepo.create(shcData).map(id => Redirect("/shopping-cart/" + id + "/form"))
    })
  }

  def createCartForm() = Action.async { implicit request =>
    val customers = ctmRepo.getAll()
    customers.map(customer => Ok(views.html.carts.cartadd(shoppingCartAddForm, customer)))
  }

  def getCarts() = Action.async { implicit request: Request[AnyContent] =>
    for {
      list <- shcRepo.getAll()
    } yield {
      Ok(Json.toJson(list))
    }
  }

  def getCartsForm() = Action.async { implicit request: Request[AnyContent] =>
    val list = shcRepo.getAll()
    list.map(cart => Ok(views.html.carts.carts(cart)))
  }

  def getCart(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    shcRepo.getById(id).map {
      case x if x.nonEmpty => Ok(Json.toJson(x))
      case _ => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def getCartForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    shcRepo.getById(id).map {
      case cart if cart.nonEmpty => Ok(views.html.carts.cart(cart.head))
      case _ => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def addToCartJSON(id: Long) = Action.async { implicit request =>
    shoppingCartProdAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { data =>
      shcRepo.addProduct(id, data.prdId, data.quantity).flatMap(x => x.map {
        case 1 => Ok(Json.obj("status" -> "OK", "message" -> "updated"))
        case 0 => BadRequest(Json.obj(
          "status" -> "Error",
          "message" -> s"Not found item by id: ${id}",
        ))
      })
    })
  }

  def addToCart(id: Long) = Action.async { implicit request =>
    shoppingCartProdAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { data =>
      shcRepo.addProduct(id, data.prdId, data.quantity).flatMap(x => x.map {
        case 1 => Redirect("/shopping-cart/" + id + "/form")
        case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
      })
    })
  }

  def addToCartForm(id: Long) = Action.async { implicit request =>
    val products = prodRepo.getAll()
    products.map(prod => Ok(views.html.carts.cart_prodadd(shoppingCartProdAddForm, prod, id)))
  }

  def removeFromCartJSON(id: Long, prodId: Long) = Action.async { implicit request =>
      shcRepo.removeProduct(id, prodId).flatMap(x => x.map {
        case 1 => Ok(Json.obj("status" -> "OK", "message" -> "updated"))
        case 0 => BadRequest(Json.obj(
          "status" -> "Error",
          "message" -> s"Not found item by id: $id",
        ))
      })
  }

  def removeFromCartForm(id: Long, prodId: Long) = Action.async { implicit request =>
    shcRepo.removeProduct(id, prodId).flatMap(x => x.map {
        case 1 => Redirect("/shopping-cart/" + id + "/form")
        case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
      })
  }

  def modifyCartJSON(id: Long) = Action.async { implicit request =>
    shoppingCartUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { shCart =>
      shcRepo.updateById(id, shCart).map {
        case 1 => Ok(Json.obj("status" -> "OK", "message" -> "updated"))
        case 0 => BadRequest(Json.obj(
          "status" -> "Error",
          "message" -> s"Not found item by id: $id",
        ))
      }
    })
  }

  def modifyCart(id: Long) = Action.async { implicit request =>
    shoppingCartUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { shCart =>
      shcRepo.updateById(id, shCart).map {
        case 1 => Redirect("/shopping-cart/" + id + "/form")
        case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
      }
    })
  }

  def modifyCartForm(id: Long) = Action.async { implicit request =>
    var customer: Seq[CustomerData] = Seq[CustomerData]()
    val customers = ctmRepo.getAll().onComplete {
      case Success(value) => customer = value
      case Failure(_) => print("fail")
    }

    var order: Iterable[WSResponseOrderData] = Seq[WSResponseOrderData]()
    val orders = ordRepo.getAll().onComplete {
      case Success(value) => order = value
      case Failure(_) => print("fail")
    }

    val cart = shcRepo.getById(id)
    cart.map {
      case x if x.nonEmpty =>
        val transactionForm = shoppingCartUpdateForm.fill(WSUpdateShoppingCartData(Option.apply(x.head.cart.value),
          Option.apply(x.head.cart.customerId), x.head.cart.orderId))
        Ok(views.html.carts.cartupdate(transactionForm, customer, order, x.head.cart.id))
      case _ => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def removeCart(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    shcRepo.removeById(id).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("removed: " + id)))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def removeCartForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    shcRepo.removeById(id).map {
      case 1 => Redirect("/shopping-cart/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

}
