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

import helpers.BaseTestSpec
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import play.api.http.Status.{ACCEPTED, BAD_REQUEST, INTERNAL_SERVER_ERROR, NO_CONTENT, OK, UNAUTHORIZED}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsJson, Result}
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers.{await, contentAsJson, defaultAwaitTimeout, status}
import uk.gov.hmrc.auth.core.BearerTokenExpired
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.Future
import scala.io.Source

class ROSMControllerSpec extends BaseTestSpec {

  lazy val rosmController = new ROSMController(
    mockAuthCon,
    mockDesConnector,
    mockTaxEnrolmentConnector,
    controllerComponents,
    mockAuditService,
    mockAppConfig
  )

  override def beforeEach(): Unit = {
    reset(mockDesConnector)
    when(mockAuthCon.authorise[Unit](any(), any())(any(), any())).thenReturn(Future.successful(()))
  }

  val regPayload: String =
    Source.fromInputStream(getClass.getResourceAsStream("/json/registration_example.json")).mkString

  val regErrorJson: String = Source.fromInputStream(getClass.getResourceAsStream("/json/utr_error.json")).mkString

  val subscribePayload: String =
    Source.fromInputStream(getClass.getResourceAsStream("/json/subscription_example.json")).mkString

  "Register endpoint" should {

    "return a 200 ok response" when {
      "everything is valid and no errors are thrown" in {

        when(mockDesConnector.register(any(), any())(any())).thenReturn(Future.successful(HttpResponse(OK, "{}")))

        doRegister() { res =>
          status(res) mustBe OK
        }
      }
    }

    "return a 400 error response with Invalid UTR as the response code" when {
      "the connector returns a 400 response" in {
        when(mockDesConnector.register(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, regErrorJson)))

        doRegister() { res =>
          status(res)                              mustBe BAD_REQUEST
          (contentAsJson(res) \ "code").as[String] mustBe "INVALID_UTR"
        }
      }
    }

    "return a 500 error response" when {
      "the connector returns an error" in {
        when(mockDesConnector.register(any(), any())(any())).thenReturn(Future.failed(new Exception("Error")))

        doRegister() { res =>
          status(res)                              mustBe INTERNAL_SERVER_ERROR
          (contentAsJson(res) \ "code").as[String] mustBe "INTERNAL_SERVER_ERROR"
        }
      }
    }

    "return unauthorised" when {
      "the auth connector does not return successfully" in {
        when(mockAuthCon.authorise[Unit](any(), any())(any(), any()))
          .thenReturn(Future.failed(BearerTokenExpired("Unauthorised")))

        doRegister() { res =>
          status(res) mustBe UNAUTHORIZED
        }
      }
    }

  }

  "Subscribe endpoint" should {

    "return a 200 ok response with the subscriptionId" when {
      "everything is valid and no errors are thrown" in {

        when(mockTaxEnrolmentConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))

        when(mockDesConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(ACCEPTED, s"""{"subscriptionId": "928282776"}""")))

        doSubscribe() { res =>
          status(res)                                        mustBe ACCEPTED
          verify(mockAuditService).audit(
            auditType = ArgumentMatchers.eq("submitSubscriptionSuccess"),
            path = ArgumentMatchers.eq("submitSubscription"),
            auditData = ArgumentMatchers.eq(
              Map(
                "response"       -> status(res).toString,
                "safeId"         -> "XE0001234567890",
                "lisaManagerRef" -> "Z1234",
                "subscriptionId" -> "928282776"
              )
            )
          )(any[HeaderCarrier])

          (contentAsJson(res) \ "subscriptionId").as[String] mustBe "928282776"
        }
      }
    }

    "return a 500 internal server error response" when {

      "the call to des fails" in {
        when(mockTaxEnrolmentConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))

        when(mockDesConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.failed(UpstreamErrorResponse("Bad Request", BAD_REQUEST, BAD_REQUEST)))

        doSubscribe() { res =>
          status(res)                              mustBe INTERNAL_SERVER_ERROR
          verify(mockAuditService).audit(
            auditType = ArgumentMatchers.eq("submitSubscriptionFailed"),
            path = ArgumentMatchers.eq("submitSubscription"),
            auditData = ArgumentMatchers.eq(Map("error" -> "Bad Request", "lisaManagerRef" -> "Z1234"))
          )(any[HeaderCarrier])

          (contentAsJson(res) \ "code").as[String] mustBe "INTERNAL_SERVER_ERROR"
        }
      }

      "the call to des returns an unexpected status code" in {
        when(mockTaxEnrolmentConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))

        when(mockDesConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, s"""{"subscriptionId": "928282776"}""")))

        doSubscribe() { res =>
          status(res)                              mustBe INTERNAL_SERVER_ERROR
          (contentAsJson(res) \ "code").as[String] mustBe "INTERNAL_SERVER_ERROR"
        }
      }

      "the call to tax enrolments fails" in {
        when(mockTaxEnrolmentConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.failed(UpstreamErrorResponse("Bad Request", BAD_REQUEST, BAD_REQUEST)))

        when(mockDesConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(ACCEPTED, s"""{"subscriptionId": "928282776"}""")))

        doSubscribe() { res =>
          status(res)                              mustBe INTERNAL_SERVER_ERROR
          (contentAsJson(res) \ "code").as[String] mustBe "INTERNAL_SERVER_ERROR"
        }
      }

      "the call to tax enrolments returns an unexpected status code" in {
        when(mockTaxEnrolmentConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(ACCEPTED, "")))

        when(mockDesConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(ACCEPTED, s"""{"subscriptionId": "928282776"}""")))

        doSubscribe() { res =>
          status(res)                              mustBe INTERNAL_SERVER_ERROR
          (contentAsJson(res) \ "code").as[String] mustBe "INTERNAL_SERVER_ERROR"
        }
      }

      "the response from des does not contain a subscriptionId" in {
        when(mockTaxEnrolmentConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))

        when(mockDesConnector.subscribe(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(ACCEPTED, s"""{}""")))

        doSubscribe() { res =>
          status(res)                              mustBe INTERNAL_SERVER_ERROR
          (contentAsJson(res) \ "code").as[String] mustBe "INTERNAL_SERVER_ERROR"
        }
      }

    }

    "return unauthorised" when {
      "the auth connector does not return successfully" in {
        when(mockAuthCon.authorise[Unit](any(), any())(any(), any()))
          .thenReturn(Future.failed(BearerTokenExpired("Unauthorised")))

        doSubscribe() { res =>
          status(res) mustBe UNAUTHORIZED
        }
      }
    }
  }

  "callback endpoint" should {
    "return a no content if called" in {
      status(
        rosmController
          .subscriptionCallback()
          .apply(
            FakeRequest(Helpers.GET, "/rosm/callback?subscriptionId=123456")
              .withBody(AnyContentAsJson(Json.obj("some" -> "body")))
          )
      ) mustBe NO_CONTENT
    }
  }

  def doRegister()(callback: Future[Result] => Unit): Unit = {
    val res = await(
      rosmController
        .register("1234567890")
        .apply(FakeRequest(Helpers.PUT, "/").withBody(AnyContentAsJson(Json.parse(regPayload))))
    )

    callback(Future(res))
  }

  def doSubscribe()(callback: Future[Result] => Unit): Unit = {
    val res = await(
      rosmController
        .submitSubscription("1234567890", "Z1234")
        .apply(FakeRequest(Helpers.PUT, "/").withBody(AnyContentAsJson(Json.parse(subscribePayload))))
    )

    callback(Future(res))
  }

}
