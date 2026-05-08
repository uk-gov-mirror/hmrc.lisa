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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR, NO_CONTENT}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier
import utils.ConnectorSpecHelper

import scala.concurrent.ExecutionContext

class TaxEnrolmentConnectorSpec extends ConnectorSpecHelper {

  given hc: HeaderCarrier    = HeaderCarrier()
  given ec: ExecutionContext = injector.instanceOf[ExecutionContext]

  // lazy to allow wiremock to start
  lazy val taxEnrolmentConnector: TaxEnrolmentConnector = injector.instanceOf[TaxEnrolmentConnector]

  private val enrolmentStatusUrl = "/tax-enrolments/groups/Z0192/subscriptions"
  private val subscribeUrl       = "/tax-enrolments/subscriptions/1234567890/subscriber"

  "Get enrolment status" should {
    "return a success verbatim when a successful response is returned from tax enrolment" in {
      stubForGet(enrolmentStatusUrl, ACCEPTED, """{"status": "PENDING"}""")

      val response = await(taxEnrolmentConnector.enrolmentStatus("Z0192"))

      response.status           mustBe ACCEPTED
      Json.parse(response.body) mustBe Json.parse("""{"status": "PENDING"}""")
      verifyTaxEnrolmentGet(enrolmentStatusUrl)
    }

    "return an error verbatim when an error is returned from tax enrolment" in {
      stubForGet(enrolmentStatusUrl, INTERNAL_SERVER_ERROR, """{"code": "INTERNAL_ERROR"}""")

      val response = await(taxEnrolmentConnector.enrolmentStatus("Z0192"))

      response.status           mustBe INTERNAL_SERVER_ERROR
      Json.parse(response.body) mustBe Json.parse("""{"code": "INTERNAL_ERROR"}""")
      verifyTaxEnrolmentGet(enrolmentStatusUrl)
    }
  }

  "Subscribe" should {
    "return a success verbatim when a successful response is returned from tax enrolment" in {
      val payload = Json.parse("{}")
      stubForPut(subscribeUrl, NO_CONTENT, "")

      val response = await(taxEnrolmentConnector.subscribe("1234567890", payload))

      response.status mustBe NO_CONTENT
      response.body   mustBe ""
      verifyTaxEnrolmentPut(subscribeUrl, payload)
    }

    "return an error verbatim when an error is returned from tax enrolment" in {
      stubForPut(subscribeUrl, INTERNAL_SERVER_ERROR, """{"code": "INTERNAL_ERROR"}""")

      val payload  = Json.parse("{}")
      val response = await(taxEnrolmentConnector.subscribe("1234567890", payload))

      response.status           mustBe INTERNAL_SERVER_ERROR
      Json.parse(response.body) mustBe Json.parse("""{"code": "INTERNAL_ERROR"}""")
      verifyTaxEnrolmentPut(subscribeUrl, payload)
    }
  }

  def verifyTaxEnrolmentGet(url: String): Unit =
    server.verify(
      getRequestedFor(urlEqualTo(url))
        .withHeader("CorrelationId", matching(uuidPattern))
    )

  def verifyTaxEnrolmentPut(url: String, expectedBody: JsValue): Unit =
    server.verify(
      putRequestedFor(urlEqualTo(url))
        .withHeader("CorrelationId", matching(uuidPattern))
        .withRequestBody(equalToJson(expectedBody.toString))
    )

}
