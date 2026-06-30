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

import config.AppConfig
import connectors.{LisaRoutingConnector, TaxEnrolmentConnector}
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request, Result, Results}
import services.AuditService
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, AuthProviders, AuthorisedFunctions}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ROSMController @Inject() (
  override val authConnector: AuthConnector,
  connector: LisaRoutingConnector,
  enrolmentConnector: TaxEnrolmentConnector,
  cc: ControllerComponents,
  auditService: AuditService,
  appConfig: AppConfig
)(using ec: ExecutionContext)
    extends BackendController(cc: ControllerComponents) with AuthorisedFunctions with Logging {

  def register(utr: String): Action[AnyContent] = Action.async { implicit request =>
    authorised(AffinityGroup.Organisation and AuthProviders(GovernmentGateway)) {
      performRegister(utr)(using request)
    } recover { case _ =>
      Unauthorized
    }
  }

  private def performRegister(utr: String)(using request: Request[AnyContent]): Future[Result] =
    connector.register(utr, request.body.asJson.get).map { response =>
      logger.info(s"[ROSMController][performRegister] The connector has returned ${response.status} for $utr")
      Results.Status(response.status)(response.body)
    } recover { case NonFatal(ex: Throwable) =>
      logger.error(
        s"[ROSMController][performRegister] Failed for UTR : $utr - ${ex.getMessage} returning INTERNAL_SERVER_ERROR"
      )
      InternalServerError(
        """{"code":"INTERNAL_SERVER_ERROR","reason":"Dependent systems are currently not responding"}"""
      )
    }

  def submitSubscription(utr: String, lisaManagerRef: String): Action[AnyContent] = Action.async { implicit request =>
    authorised(AffinityGroup.Organisation and AuthProviders(GovernmentGateway)) {
      val requestJson: JsValue = request.body.asJson.get
      connector.subscribe(lisaManagerRef, requestJson).flatMap { response =>
        logger.info(
          s"[ROSMController][submitSubscription Response from Connector ${response.status} for $utr, lisaManager : $lisaManagerRef"
        )

        response.status match {
          case ACCEPTED =>
            val success        = Results.Status(response.status)(response.body)
            val safeId         = (requestJson \ "safeId").as[String]
            
            val subscriptionId = (response.json \ "success" \ "subscriptionId").as[String]

            logger.info(
              s"[ROSMController][submitSubscription] calling Tax Enrolments with subscriptionId $subscriptionId and safeId $safeId"
            )

            auditService.audit(
              auditType = "submitSubscriptionSuccess",
              path = "submitSubscription",
              auditData = Map(
                "response"       -> response.status.toString,
                "safeId"         -> safeId,
                "lisaManagerRef" -> lisaManagerRef,
                "subscriptionId" -> subscriptionId
              )
            )

            submitTaxEnrolmentSubscription(subscriptionId, safeId, success)
          case _        =>
            logger.warn(
              s"[ROSMController][submitSubscription] ROSM subscription failed with code ${response.status} for zref $lisaManagerRef"
            )
            auditService.audit(
              auditType = "submitSubscriptionFailed",
              path = "submitSubscription",
              auditData = Map("response" -> response.status.toString, "lisaManagerRef" -> lisaManagerRef)
            )
            throw new RuntimeException(
              s"ROSM subscription failed. Returned a response status of ${response.status} for zref $lisaManagerRef"
            )
        }
      } recover { case NonFatal(ex: Throwable) =>
        auditService.audit(
          auditType = "submitSubscriptionFailed",
          path = "submitSubscription",
          auditData = Map("error" -> ex.getMessage, "lisaManagerRef" -> lisaManagerRef)
        )
        logger.error(s"[ROSMController][submitSubscription] Failed - ${ex.getMessage} for zref $lisaManagerRef")
        InternalServerError(
          """{"code":"INTERNAL_SERVER_ERROR","reason":"Dependent systems are currently not responding"}"""
        )
      }
    } recover { case _ =>
      Unauthorized
    }
  }

  private def submitTaxEnrolmentSubscription(subscriptionId: String, safeId: String, success: Result)(using
    hc: HeaderCarrier
  ): Future[Result] = {
    val enrolmentRequest =
      Json.obj("serviceName" -> "HMRC-LISA-ORG", "callback" -> appConfig.rosmCallbackUrl, "etmpId" -> safeId)

    enrolmentConnector.subscribe(subscriptionId, enrolmentRequest)(using hc).map { enrolRes =>
      logger.info(
        s"[ROSMController][submitSubscription] Tax Enrolments: Response from Connector ${enrolRes.status} for $subscriptionId"
      )

      enrolRes.status match {
        case NO_CONTENT => success
        case _          =>
          val msg =
            s"Tax Enrolment subscription failed. Returned a response status of ${enrolRes.status} for subscriptionId $subscriptionId and safeId $safeId"
          throw new RuntimeException(msg)
      }
    }
  }

  def subscriptionCallback: Action[AnyContent] = Action.async { request =>
    val logDetails =
      s"method = ${request.method}" +
        request.getQueryString("subscriptionId").map(id => s", subscriptionId = $id").getOrElse("") +
        request.body.asJson.map(js => s", json = ${js.toString}").getOrElse("")

    logger.warn(s"[ROSMController][submitSubscription] Received ROSM subscription callback: $logDetails")

    Future.successful(NoContent)
  }

}
