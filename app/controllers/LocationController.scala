package controllers

import models.repository.LocationRepository
import models.{WSLocationData, WSUpdateLocationData}
import play.api.data.Forms.{mapping, nonEmptyText, optional}
import play.api.data.{Form, FormError}
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class LocationController @Inject()(val lcnRepo: LocationRepository,
                                   cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = Json.obj(
      "key" -> Json.toJson(o.key),
      "message" -> Json.toJson(o.message)
    )
  }

  val locationAddForm: Form[WSLocationData] = Form {
    mapping(
      "code" -> nonEmptyText,
      "type" -> nonEmptyText,
      "description" -> nonEmptyText,
    )(WSLocationData.apply)(WSLocationData.unapply)
  }

  val locationUpdateForm: Form[WSUpdateLocationData] = Form {
    mapping(
      "code" -> optional(nonEmptyText),
      "type" -> optional(nonEmptyText),
      "description" -> optional(nonEmptyText),
    )(WSUpdateLocationData.apply)(WSUpdateLocationData.unapply)
  }


  def getLocations() = Action.async { implicit request: Request[AnyContent] =>
    for {
      list <- lcnRepo.getAll()
    } yield {
      val json = JsObject(Seq(
        "locations" -> Json.toJson(list)
      ))
      Ok(json)
    }
  }

  def getLocationsForm() = Action.async { implicit request: Request[AnyContent] =>
    val list = lcnRepo.getAll()
    list.map(location => Ok(views.html.locations.locations(location)))
  }

  def addLocationJSON() = Action.async { implicit request =>
    locationAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    },
      { locData =>
          try {
            lcnRepo.create(locData).map(id =>
              Ok(Json.obj("status" -> "OK", "message" -> id)))
          }
          catch {
            case ex: NoSuchElementException => Future.successful(BadRequest(Json.obj(
              "status" -> "Error",
              "message" -> ex.getMessage
            )))
            case _ => Future.successful(InternalServerError("unknown error"))
          }
      })
  }

  def addLocation() = Action.async { implicit request =>
    locationAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    },
      { locData =>
          try {
            lcnRepo.create(locData).map(id =>
              Redirect("/locations/" + id + "/form"))
          }
          catch {
            case ex: NoSuchElementException => Future.successful(BadRequest(views.html.businesserror(ex.getMessage)))
            case _ => Future.successful(InternalServerError(views.html.businesserror("unknown error")))
          }
      })
  }

  def addLocationForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.locations.locationadd(locationAddForm))
  }

  def getLocation(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    lcnRepo.getById(id).map {
      case Some(p) => Ok(Json.toJson(p))
      case None => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def getLocationForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    lcnRepo.getById(id).map {
      case Some(l) => Ok(views.html.locations.location(l))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def modifyLocationJSON(id: Long) = Action.async { implicit request =>
    locationUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { loc =>
      try {
        lcnRepo.updateById(id, loc).map {
          case 1 => Ok(Json.obj("status" -> "OK", "message" -> id))
          case 0 => BadRequest(Json.obj(
            "status" -> "Error",
            "message" -> s"Not found item by id: $id",
          ))
        }
      }
      catch {
        case ex: NoSuchElementException => Future.successful(BadRequest(Json.obj(
          "status" -> "Error",
          "message" -> ex.getMessage
        )))
        case _ => Future.successful(InternalServerError("unknown error"))
      }
    })
  }

  def modifyLocation(id: Long) = Action.async { implicit request =>
    locationUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { loc =>
      try {
        lcnRepo.updateById(id, loc).map {
          case 1 => Redirect("/locations/" + id + "/form")
          case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
        }
      }
      catch {
        case ex: NoSuchElementException => Future.successful(BadRequest(views.html.businesserror(ex.getMessage)))
        case _ => Future.successful(InternalServerError("unknown error"))
      }
    })
  }

  def modifyLocationForm(id: Long) = Action.async { implicit request =>
    val lcn = lcnRepo.getById(id)
    lcn.map {
      case Some(x) =>
        val lcnForm = locationUpdateForm.fill(WSUpdateLocationData(Option.apply(x.code), Option.apply(x.locType), Option.apply(x.description)))
        Ok(views.html.locations.locationupdate(lcnForm, x.id))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def removeLocation(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    lcnRepo.removeById(id).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> ("removed: " + id)))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def removeLocationForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    lcnRepo.removeById(id).map {
      case 1 => Redirect("/locations/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

}
