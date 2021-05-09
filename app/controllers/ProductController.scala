package controllers

import models.repository.{CategoryRepository, ProductRepository}
import models.{CategoryData, WSProductData, WSUpdateProductData}
import play.api.data.{Form, FormError}
import play.api.data.Forms._
import play.api.data.format.Formats.doubleFormat
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class ProductController @Inject()(val prdRepo: ProductRepository, catRepo: CategoryRepository,
                                  cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = Json.obj(
      "key" -> Json.toJson(o.key),
      "message" -> Json.toJson(o.message)
    )
  }

  val productAddForm: Form[WSProductData] = Form {
    mapping(
      "name" -> nonEmptyText,
      "code" -> nonEmptyText,
      "description" -> nonEmptyText,
      "value" -> of[Double],
      "categoryId" -> longNumber
    )(WSProductData.apply)(WSProductData.unapply)
  }

  val productUpdateForm: Form[WSUpdateProductData] = Form {
    mapping(
      "name" -> optional(nonEmptyText),
      "code" -> optional(nonEmptyText),
      "description" -> optional(nonEmptyText),
      "value" -> optional(of[Double]),
      "categoryId" -> optional(longNumber)
    )(WSUpdateProductData.apply)(WSUpdateProductData.unapply)
  }


  def getProducts() = Action.async { implicit request: Request[AnyContent] =>
    for {
      list <- prdRepo.getAll()
    } yield {
      val json = JsObject(Seq(
        "products" -> Json.toJson(list)
      ))
      Ok(json)
    }
  }

  def getProductsForm() = Action.async { implicit request: Request[AnyContent] =>
    val list = prdRepo.getAll()
    list.map(prd => Ok(views.html.products.products(prd)))
  }

  def addProductJSON = Action.async { implicit request =>

    var categ: Seq[CategoryData] = Seq[CategoryData]()
    val categories = catRepo.getAll().onComplete {
      case Success(value) => categ = value
      case Failure(_) => print("fail")
    }

    productAddForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(Json.obj(
          "status" -> "Error",
          "details" -> Json.toJson(errorForm.errors)
        )))
      }, pData =>
        prdRepo.create(pData.name, pData.code, pData.description, pData.categoryId, pData.value).map(id =>
          Ok(Json.obj("status" -> "OK", "message" -> ("created " + id))))
    )
  }

  def addProduct = Action.async { implicit request =>

    var categ: Seq[CategoryData] = Seq[CategoryData]()
    val categories = catRepo.getAll().onComplete {
      case Success(value) => categ = value
      case Failure(_) => print("fail")
    }

    productAddForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(views.html.error(errorForm.errors)))
      }, pData =>
        prdRepo.create(pData.name, pData.code, pData.description, pData.categoryId, pData.value).map(id =>
          Redirect("/products/" + id + "/form"))
    )
  }

  def updateProduct(id: Long) = Action.async { implicit request =>
    productUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(views.html.error(errorForm.errors)))
    }, { p =>
      prdRepo.updateById(id, p).map {
        case 1 => Redirect("/products/" + id + "/form")
        case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
      }
    })
  }

  def updateProductJSON(id: Long) = Action.async { implicit request =>
    productUpdateForm.bindFromRequest.fold(errorForm => {
      Future.successful(BadRequest(Json.obj(
        "status" -> "Error",
        "details" -> Json.toJson(errorForm.errors)
      )))
    }, { p =>
      prdRepo.updateById(id, p).map {
        case 1 => Ok(Json.obj("status" -> "OK", "message" -> "updated"))
        case 0 => BadRequest(Json.obj(
          "status" -> "Error",
          "message" -> s"Not found item by id: $id",
        ))
      }
    })
  }

  def updateProductForm(id: Long) = Action.async { implicit request =>

    var categ: Seq[CategoryData] = Seq[CategoryData]()
    val categories = catRepo.getAll().onComplete {
      case Success(value) => categ = value
      case Failure(_) => print("fail")
    }

    val prd = prdRepo.getById(id)
    prd.map {
      case Some(x) =>
        val prdForm = productUpdateForm.fill(WSUpdateProductData(Option.apply(x.name), Option.apply(x.code), Option.apply(x.description),
          Option.apply(x.value), Option.apply(x.categoryId)))
        Ok(views.html.products.productupdate(prdForm, categ, x.id))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def getProduct(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    prdRepo.getById(id).map {
      case Some(p) => Ok(Json.toJson(p))
      case None => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def getProductForm(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    prdRepo.getById(id).map {
      case Some(p) => Ok(views.html.products.product(p))
      case None => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }

  def removeProduct(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    prdRepo.removeById(id).map {
      case 1 => Ok(Json.obj("status" -> "OK", "message" -> "removed"))
      case 0 => BadRequest(Json.obj(
        "status" -> "Error",
        "message" -> s"Not found item by id: $id",
      ))
    }
  }

  def removeProductForm(id: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    prdRepo.removeById(id).map {
      case 1 => Redirect("/products/form")
      case 0 => BadRequest(views.html.businesserror(s"Not found item by id: $id"))
    }
  }


  def addProductForm: Action[AnyContent] = Action.async { implicit request: MessagesRequest[AnyContent] =>
    val categories = catRepo.getAll()
    categories.map(cat => Ok(views.html.products.productadd(productAddForm, cat)))
  }

}
