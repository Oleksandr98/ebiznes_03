package controllers

import models.repository.{CardRepository, CustomerRepository}
import models.{CardStatuses, CustomerData, WSCardData, WSUpdateCardData}
import play.api.data.Forms.{longNumber, mapping, nonEmptyText, optional}
import play.api.data.{Form, FormError}
import play.api.libs.json._
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class CardController @Inject()(val crdRepo: CardRepository, custRepo: CustomerRepository,
                               cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = Json.obj(
      "key" -> Json.toJson(o.key),
      "message" -> Json.toJson(o.message)
    )
  }

  val cardAddForm: Form[WSCardData] = Form {
    mapping(
      "number" -> nonEmptyText,
      "customerId" -> optional(longNumber)
    )(WSCardData.apply)(WSCardData.unapply)
  }

  val cardUpdateForm: Form[WSUpdateCardData] = Form {
    mapping(
      "number" -> optional(nonEmptyText),
      "status" -> optional(nonEmptyText),
      "customerId" -> optional(longNumber)
    )(WSUpdateCardData.apply)(WSUpdateCardData.unapply)
  }

  def getCards() = Action.async { implicit request: Request[AnyContent] =>
    for {
      list <- crdRepo.getAll()
    } yield {
      val json = JsObject(Seq(
        "cards" -> Json.toJson(list)
      ))
      Ok(json)
    }
  }

  def getCardsFrom() = Action.async { implicit request: Request[AnyContent] =>
    val list = crdRepo.getAll()
    list.map(card => Ok(views.html.cards.cards(card)))
  }

  def addCardJSON() = Action.async { implicit request =>
    cardAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { cData =>
      crdRepo.create(cData.number, cData.customerId).map(id =>
        Ok(Json.obj("status" -> "OK", "message" -> ("created " + id))))
    })
  }

  def addCard() = Action.async { implicit request =>
    cardAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { cData =>
      crdRepo.create(cData.number, cData.customerId).map(id =>
        Redirect("/cards/" + id + "/form"))
    })
  }

  def addCardForm() = Action.async { implicit request: MessagesRequest[AnyContent] =>
    val customers = custRepo.getAll()
    customers.map(customer => Ok(views.html.cards.cardadd(cardAddForm, customer)))
  }

  def closeCard(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    crdRepo.closeById(id).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("closed: " + id)))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def closeCardForm(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    crdRepo.closeById(id).map {
      case 1 => Redirect("/cards/" + id + "/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def blockCard(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    crdRepo.updateById(id, new WSUpdateCardData(Option.empty, Option.apply(CardStatuses.Blocked.toString), Option.empty)).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("blocked: " + id)))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def blockCardForm(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    crdRepo.updateById(id, new WSUpdateCardData(Option.empty, Option.apply(CardStatuses.Blocked.toString), Option.empty)).map {
      case 1 => Redirect("/cards/" + id + "/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def getCardForm(id: Long): Action[AnyContent] = Action.async { implicit request =>
    crdRepo.getById(id).map {
      case Some(p) => Ok(views.html.cards.card(p))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def getCardJSON(id: Long): Action[AnyContent] = Action.async { implicit request =>
    crdRepo.getById(id).map {
      case Some(p) => Ok(Json.toJson(p))
      case None => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def modifyCard(id: Long) = Action.async { implicit request =>
    cardUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { cData =>
      crdRepo.updateById(id, cData).map {
        case 1 => Redirect("/cards/" + id + "/form")
        case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
      }
    })
  }

  def modifyCardJSON(id: Long) = Action.async { implicit request =>
    cardUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { cData =>
      crdRepo.updateById(id, cData).map {
        case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("updated: " + id)))
        case 0 => BadRequest(s"Not found item by id: $id")
      }
    })
  }

  def modifyCardForm(id: Long) = Action.async { implicit request =>

    var customer: Seq[CustomerData] = Seq[CustomerData]()
    val customers = custRepo.getAll().onComplete {
      case Success(value) => customer = value
      case Failure(_) => print("fail")
    }

    val card = crdRepo.getById(id)
    card.map {
      case Some(x) =>
        val cardForm = cardUpdateForm.fill(WSUpdateCardData(Option.apply(x.number), Option.apply(x.status), x.customerId))
        Ok(views.html.cards.cardupdate(cardForm, customer, x.id))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

}
