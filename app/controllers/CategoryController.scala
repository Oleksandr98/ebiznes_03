package controllers

import models.repository.CategoryRepository
import models.{CategoryData, WSCategoryData, WSUpdateCategoryData}
import play.api.data.Forms.{mapping, nonEmptyText, optional, text}
import play.api.data.{Form, FormError}
import play.api.libs.json._
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class CategoryController @Inject()(val catRepo: CategoryRepository, cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {


  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = Json.obj(
      "key" -> Json.toJson(o.key),
      "message" -> Json.toJson(o.message)
    )
  }

  val categoryAddForm: Form[WSCategoryData] = Form {
    mapping(
      "name" -> nonEmptyText,
      "code" -> nonEmptyText,
    )(WSCategoryData.apply)(WSCategoryData.unapply)
  }

  val categoryUpdateForm: Form[WSUpdateCategoryData] = Form {
    mapping(
      "name" -> optional(nonEmptyText),
      "code" -> optional(nonEmptyText),
    )(WSUpdateCategoryData.apply)(WSUpdateCategoryData.unapply)
  }


  def getCategories() = Action.async { implicit request: Request[AnyContent] =>
    for {
      list <- catRepo.getAll()
    } yield {
      val json = JsObject(Seq(
        "categories" -> Json.toJson(list)
      ))
      Ok(json)
    }
  }

  def getCategoriesForm() = Action.async { implicit request: Request[AnyContent] =>
    val list = catRepo.getAll()
    list.map(cat => Ok(views.html.categories.categories(cat)))
  }

  def addCategoryJSON = Action.async { implicit request =>
    categoryAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, category => {
      catRepo.create(category.name, category.code).map(id =>
        Ok(Json.obj("status" -> "OK", "message" -> ("created: " + id))))
    })
  }

  def addCategory = Action.async { implicit request =>
    categoryAddForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, category => {
      catRepo.create(category.name, category.code).map(id =>
        Redirect("/categories/form"))
    })
  }

  def addCategoryForm = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.categories.categoryadd(categoryAddForm))
  }

  def removeCategoryForm(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    catRepo.removeById(id).map {
      case 1 => Redirect("/categories/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def removeCategoryJSON(id: Long) = Action.async { implicit request: Request[AnyContent] =>
    catRepo.removeById(id).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> "removed"))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def modifyCategoryJSON(id: Long) = Action.async { implicit request =>
    categoryUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { c =>
      catRepo.updateById(id, c).map {
        case 1 => Ok(Json.obj("status" -> "OK", "message" -> "updated"))
        case 0 => BadRequest(Json.obj(
          "status" -> "Error",
          "message" -> s"Not found item by id: $id",
        ))
      }
    })
  }

  def modifyCategoryForm(id: Long) = Action.async { implicit request =>
    val cat = catRepo.getById(id)
    cat.map {
      case Some(x) =>
        val catForm = categoryUpdateForm.fill(WSUpdateCategoryData(Option.apply(x.name), Option.apply(x.code)))
        Ok(views.html.categories.categoryupdate(catForm, x.id))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def modifyCategory(id: Long) = Action.async { implicit request =>
    categoryUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { c =>
      catRepo.updateById(id, c).map {
        case 1 => Redirect("/categories/" + id + "/form")
        case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
      }
    })
  }

  def getCategoryForm(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    catRepo.getById(id).map {
      case Some(p) => Ok(views.html.categories.category(p))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def getCategoryJSON(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    catRepo.getById(id).map {
      case Some(p) => Ok(Json.toJson(p))
      case None => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }
}
