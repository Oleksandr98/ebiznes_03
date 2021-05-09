package controllers

import play.api.mvc._

import javax.inject._

@Singleton
class WelcomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index.render())
  }

}
