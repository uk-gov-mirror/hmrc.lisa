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

import base.BaseTestSpec
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.BearerTokenExpired
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import scala.concurrent.Future

class TaxEnrolmentControllerSpec extends BaseTestSpec {

  lazy val taxEnrolmentController =
    new TaxEnrolmentController(mockAuthCon, mockTaxEnrolmentConnector, controllerComponents)

  override def beforeEach(): Unit = {
    reset(mockTaxEnrolmentConnector)
    when(mockAuthCon.authorise[Unit](any(), any())(any(), any())).thenReturn(Future.successful(()))
  }

  "Get Enrolments for Group ID" should {
    "return the status and body as returned from the connector" when {
      "no errors occur" in {
        when(mockTaxEnrolmentConnector.enrolmentStatus(any())(using any()))
          .thenReturn(Future.successful(HttpResponse(OK, "test")))

        val res = doGetSubscriptionsForGroupId()

        status(res)          mustBe OK
        contentAsString(res) mustBe "test"
      }
    }
    "return appropriate 500 internal server error response" when {
      "any errors occur" in {
        when(mockTaxEnrolmentConnector.enrolmentStatus(any())(using any()))
          .thenReturn(Future.failed(UpstreamErrorResponse("fail", BAD_REQUEST, BAD_REQUEST)))

        val res = doGetSubscriptionsForGroupId()

        status(res)        mustBe INTERNAL_SERVER_ERROR
        contentAsJson(res) mustBe Json.parse(
          """{"code":"INTERNAL_SERVER_ERROR","reason":"Dependent systems are currently not responding"}"""
        )
      }
    }
    "return unauthorised" when {
      "the auth connector doesnt return successfully" in {
        when(mockAuthCon.authorise[Unit](any(), any())(any(), any()))
          .thenReturn(Future.failed(BearerTokenExpired("unauthorised")))

        val res = doGetSubscriptionsForGroupId()

        status(res) mustBe UNAUTHORIZED
      }
    }

  }

  private def doGetSubscriptionsForGroupId() =
    taxEnrolmentController.getSubscriptionsForGroupId("1234567890").apply(FakeRequest(Helpers.GET, "/"))

}
