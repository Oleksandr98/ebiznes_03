package controllers

import models.repository.OffersRepository
import models.{WSOfferData, WSUpdateOfferData}
import play.api.data.Forms.{mapping, nonEmptyText, optional, sqlDate}
import play.api.data.{Form, FormError}
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class OffersController @Inject()(val ofrRepo: OffersRepository, cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = Json.obj(
      "key" -> Json.toJson(o.key),
      "message" -> Json.toJson(o.message)
    )
  }

  val offerAddForm: Form[WSOfferData] = Form {
    mapping(
      "code" -> nonEmptyText,
      "name" -> nonEmptyText,
      "description" -> nonEmptyText,
      "startDate" -> sqlDate,
      "endDate" -> optional(sqlDate),
    )(WSOfferData.apply)(WSOfferData.unapply)
  }

  val offerUpdateForm: Form[WSUpdateOfferData] = Form {
    mapping(
      "code" -> optional(nonEmptyText),
      "name" -> optional(nonEmptyText),
      "description" -> optional(nonEmptyText),
      "startDate" -> optional(sqlDate),
      "endDate" -> optional(sqlDate),
    )(WSUpdateOfferData.apply)(WSUpdateOfferData.unapply)
  }

  def getOffers() = Action.async { implicit request: Request[AnyContent] =>
    for {
      list <- ofrRepo.getAll()
    } yield {
      val json = JsObject(Seq(
        "offers" -> Json.toJson(list)
      ))
      Ok(json)
    }
  }

  def getOffersForm() = Action.async { implicit request: Request[AnyContent] =>
    val list = ofrRepo.getAll()
    list.map(offer => Ok(views.html.offers.offers(offer)))
  }

  def getOffer(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ofrRepo.getById(id).map {
      case Some(o) => Ok(Json.toJson(o))
      case None => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def getOfferForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ofrRepo.getById(id).map {
      case Some(o) => Ok(views.html.offers.offer(o))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }


  def addOfferJSON() = Action.async { implicit request =>
    offerAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { offerData =>
      ofrRepo.create(offerData).map(id =>
        Ok(Json.obj("status" -> "OK", "message" -> ("created: " + id))))
    })
  }

  def addOffer() = Action.async { implicit request =>
    offerAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { offerData =>
      ofrRepo.create(offerData).map(id => Redirect("/offers/" + id + "/form"))
    })
  }

  def addOfferForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.offers.offeradd(offerAddForm))
  }

  def removeOffer(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ofrRepo.removeById(id).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("removed: " + id)))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def removeOfferForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    ofrRepo.removeById(id).map {
      case 1 => Redirect("/offers/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def modifyOfferJSON(id: Long) = Action.async { implicit request =>
   offerUpdateForm.bindFromRequest.fold(errorForm => {
     Future.successful(BadRequest(Json.obj(
       "status" -> "Error",
       "details" -> Json.toJson(errorForm.errors)
     )))
   }, { ofrData =>
      ofrRepo.updateById(id, ofrData).map {
        case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("updated: " + id)))
        case 0 => BadRequest(Json.obj(
          "status" -> "Error",
          "message" -> s"Not found item by id: $id",
        ))
      }
    })
  }

  def modifyOffer(id: Long) = Action.async { implicit request =>
    offerUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { ofrData =>
      ofrRepo.updateById(id, ofrData).map {
        case 1 => Redirect("/offers/" + id + "/form")
        case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
      }
    })
  }

  def modifyOfferForm(id: Long) = Action.async { implicit request =>
    val offer = ofrRepo.getById(id)
    offer.map {
      case Some(x) =>
        val offerForm = offerUpdateForm.fill(WSUpdateOfferData(Option.apply(x.code), Option.apply(x.name), Option.apply(x.description),
          Option.apply(x.startDate), if (x.endDate.isDefined) x.endDate else Option.empty))
        Ok(views.html.offers.offerupdate(offerForm, x.id))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

}
