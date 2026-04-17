/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import connectors.TaxEnrolmentConnector
import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents, Results}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, AuthProviders, AuthorisedFunctions}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TaxEnrolmentController @Inject() (
  override val authConnector: AuthConnector,
  connector: TaxEnrolmentConnector,
  val cc: ControllerComponents
)(using ec: ExecutionContext)
    extends BackendController(cc: ControllerComponents) with AuthorisedFunctions with Logging {

  def getSubscriptionsForGroupId(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    authorised(AffinityGroup.Organisation and AuthProviders(GovernmentGateway)) {
      connector.enrolmentStatus(groupId)(using hc).map { response =>
        logger.info(
          s"[TaxEnrolmentController][getSubscriptionsForGroupId] The connector has returned ${response.status} for $groupId"
        )
        Results.Status(response.status)(response.body)
      } recover { case _ =>
        logger.error(
          s"[TaxEnrolmentController][getSubscriptionsForGroupId] Dependent systems are currently not responding returing INTERNAL_SERVER_ERROR "
        )
        InternalServerError(
          """{"code":"INTERNAL_SERVER_ERROR","reason":"Dependent systems are currently not responding"}"""
        )
      }
    } recover { case _ =>
      Unauthorized
    }
  }

}
