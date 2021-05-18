package controllers

import models.repository.CustomerRepository
import models.{WSCustomerData, WSUpdateCustomerData}
import play.api.data.Forms._
import play.api.data.{Form, FormError}
import play.api.libs.json._
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CustomerController @Inject()(val ctmRepo: CustomerRepository,
                                   cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = Json.obj(
      "key" -> Json.toJson(o.key),
      "message" -> Json.toJson(o.message)
    )
  }

  val customerAddForm: Form[WSCustomerData] = Form {
    mapping(
      "name" -> nonEmptyText,
      "surname" -> nonEmptyText,
      "password" -> nonEmptyText,
      "login" -> nonEmptyText,
      "birthDate" -> sqlDate,
    )(WSCustomerData.apply)(WSCustomerData.unapply)
  }

  val customerUpdateForm: Form[WSUpdateCustomerData] = Form {
    mapping(
      "name" -> optional(nonEmptyText),
      "surname" -> optional(nonEmptyText),
      "password" -> optional(nonEmptyText),
      "login" -> optional(nonEmptyText),
      "birthDate" -> optional(sqlDate),
    )(WSUpdateCustomerData.apply)(WSUpdateCustomerData.unapply)
  }

  def getCustomers() = Action.async { implicit request: Request[AnyContent] =>
    for {
      list <- ctmRepo.getAll()
    } yield {
      Ok(Json.toJson(list))
    }
  }

  def getCustomer(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ctmRepo.getById(id).map {
      case Some(c) => Ok(Json.toJson(c))
      case None => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def getCustomerForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ctmRepo.getById(id).map {
      case Some(c) => Ok(views.html.customers.customer(c))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def getCustomersForm() = Action.async { implicit request: Request[AnyContent] =>
    val list = ctmRepo.getAll()
    list.map(customer => Ok(views.html.customers.customers(customer)))
  }

  // createCustomer
  def enrollCustomerJSON() = Action.async { implicit request =>
    customerAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { cData =>
      ctmRepo.create(cData).map(id =>
        Ok(Json.obj("status" -> "OK", "message" -> id)))
    })
  }

  def enrollCustomer() = Action.async { implicit request =>
    customerAddForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(views.html.error(errorForm.errors)))
      }, custData =>
        ctmRepo.create(custData).map(id =>
          Redirect("/customers/" + id + "/form"))
    )
  }

  def enrollCustomerForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.customers.customeradd(customerAddForm))
  }

  def modifyCustomerJSON(id: Long) = Action.async { implicit request =>
    customerUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { c =>
      ctmRepo.updateById(id, c).map {
        case 1 => Ok(Json.obj("status" -> "OK", "message" -> id))
        case 0 => BadRequest(Json.obj(
          "status" -> "Error",
          "message" -> s"Not found item by id: $id",
        ))
      }
    })
  }

  def modifyCustomer(id: Long) = Action.async { implicit request =>
    customerUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { p =>
      ctmRepo.updateById(id, p).map {
        case 1 => Redirect("/customers/" + id + "/form")
        case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
      }
    })
  }

  def modifyCustomerForm(id: Long) = Action.async { implicit request =>
    val cust = ctmRepo.getById(id)
    cust.map {
      case Some(x) =>
        val prdForm = customerUpdateForm.fill(WSUpdateCustomerData(Option.apply(x.name), Option.apply(x.surname), Option.apply(x.password),
          Option.apply(x.login), Option.apply(x.birthDate)))
        Ok(views.html.customers.customerupdate(prdForm, x.id))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def blockCustomer(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ctmRepo.blockOrUnblockById(id, true).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> id))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def blockCustomerForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ctmRepo.blockOrUnblockById(id, true).map {
      case 1 => Redirect("/customers/" + id + "/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  // customer cannot be removed, but can be closed
  def closeCustomer(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ctmRepo.closeById(id).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> id))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def closeCustomerForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ctmRepo.closeById(id).map {
      case 1 => Redirect("/customers/" + id + "/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def unblockCustomer(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ctmRepo.blockOrUnblockById(id, false).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> id))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def unblockCustomerForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ctmRepo.blockOrUnblockById(id, false).map {
      case 1 => Redirect("/customers/" + id + "/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

}
