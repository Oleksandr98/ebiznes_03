package controllers

import models.repository.{CouponsRepository, CustomerRepository}
import models.{CouponStatuses, CustomerData, WSCouponData, WSUpdateCouponData}
import play.api.data.Forms.{longNumber, mapping, nonEmptyText, optional}
import play.api.data.{Form, FormError}
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class CouponsController @Inject()(val cpnRepo: CouponsRepository, custRepo: CustomerRepository,
                                  cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = Json.obj(
      "key" -> Json.toJson(o.key),
      "message" -> Json.toJson(o.message)
    )
  }

  val couponAddForm: Form[WSCouponData] = Form {
    mapping(
      "number" -> nonEmptyText,
      "customerId" -> optional(longNumber)
    )(WSCouponData.apply)(WSCouponData.unapply)
  }

  val couponUpdateForm: Form[WSUpdateCouponData] = Form {
    mapping(
      "number" -> optional(nonEmptyText),
      "status" -> optional(nonEmptyText),
      "customerId" -> optional(longNumber)
    )(WSUpdateCouponData.apply)(WSUpdateCouponData.unapply)
  }


  def getCoupons() = Action.async { implicit request: Request[AnyContent] =>
    for {
      list <- cpnRepo.getAll()
    } yield {
      val json = JsObject(Seq(
        "coupons" -> Json.toJson(list)
      ))
      Ok(json)
    }
  }

  def getCouponsForm() = Action.async { implicit request: Request[AnyContent] =>
    val list = cpnRepo.getAll()
    list.map(coupon => Ok(views.html.coupons.coupons(coupon)))
  }

  def addCouponJSON() = Action.async { implicit request =>
    couponAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { couponData =>
      cpnRepo.create(couponData.number, couponData.customerId).map(id =>
        Ok(Json.obj("status" -> "OK", "message" -> ("created: " + id))))
    })
  }

  def addCoupon() = Action.async { implicit request =>
    couponAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { cData =>
      cpnRepo.create(cData.number, cData.customerId).map(id =>
        Redirect("/coupons/" + id + "/form"))
    })
  }

  def addCouponForm() = Action.async { implicit request: MessagesRequest[AnyContent] =>
    val customers = custRepo.getAll()
    customers.map(customer => Ok(views.html.coupons.couponadd(couponAddForm, customer)))
  }

  def modifyCouponJSON(id: Long) = Action.async { implicit request =>
    couponUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { cnpData =>
      cpnRepo.updateById(id, cnpData).map {
        case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("updated: " + id)))
        case 0 => BadRequest(Json.obj(
          "status" -> "Error",
          "message" -> s"Not found item by id: $id",
        ))
      }
    })
  }

  def modifyCoupon(id: Long) = Action.async { implicit request =>
    couponUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { cnpData =>
      cpnRepo.updateById(id, cnpData).map {
        case 1 => Redirect("/coupons/" + id + "/form")
        case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
      }
    })
  }

  def modifyCouponForm(id: Long) = Action.async { implicit request =>

    var customer: Seq[CustomerData] = Seq[CustomerData]()
    val customers = custRepo.getAll().onComplete {
      case Success(value) => customer = value
      case Failure(_) => print("fail")
    }

    val coupon = cpnRepo.getById(id)
    coupon.map {
      case Some(x) =>
        val couponForm = couponUpdateForm.fill(WSUpdateCouponData(Option.apply(x.number), Option.apply(x.status), x.customerId))
        Ok(views.html.coupons.couponupdate(couponForm, customer, x.id))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def removeCoupon(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    cpnRepo.removeById(id).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("removed: " + id)))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def removeCouponForm(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    cpnRepo.removeById(id).map {
      case 1 => Redirect("/coupons/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def invalidateCoupon(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    cpnRepo.updateById(id, new WSUpdateCouponData(Option.empty, Option.apply(CouponStatuses.Used.toString), Option.empty)).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("used coupon: " + id)))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def invalidateCouponForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    cpnRepo.updateById(id, new WSUpdateCouponData(Option.empty, Option.apply(CouponStatuses.Used.toString), Option.empty)).map {
      case 1 => Redirect("/coupons/" + id + "/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def getCoupon(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    cpnRepo.getById(id).map {
      case Some(p) => Ok(Json.toJson(p))
      case None => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def getCouponForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    cpnRepo.getById(id).map {
      case Some(c) => Ok(views.html.coupons.coupon(c))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

}
