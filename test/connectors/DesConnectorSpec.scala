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
import com.github.tomakehurst.wiremock.http.Fault
import play.api.http.Status.{ACCEPTED, OK, SERVICE_UNAVAILABLE}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier
import utils.ConnectorSpecHelper

import scala.concurrent.ExecutionContext

class DesConnectorSpec extends ConnectorSpecHelper {

  val uuid = "123e4567-e89b-42d3-a456-556642440000"

  given hc: HeaderCarrier    = HeaderCarrier()
  given ec: ExecutionContext = injector.instanceOf[ExecutionContext]

  lazy val desConnector: DesConnector = injector.instanceOf[DesConnector] // lazy to allow wiremock to start

  private val subscribeUrl = "/lifetime-isa/manager/Z019283/subscription"
  private val registerUrl  = "/registration/organisation/utr/Z019283"

  "Subscription endpoint" should {
    "Return a status 202 when valid json posted" in {
      val payload = loadJsonFromResource("/json/subscription_example.json")
      stubForPost(subscribeUrl, ACCEPTED, """{"SubscriptionID": "928282776"}""")

      val response = await(desConnector.subscribe("Z019283", payload))

      response.status           mustBe ACCEPTED
      Json.parse(response.body) mustBe Json.parse("""{"SubscriptionID": "928282776"}""")
      verifyDesPost(subscribeUrl, payload)
    }

    "Return a status 503 when invalid json posted" in {
      val payload   = Json.toJson(
        loadStringFromResource("/json/subscription_example.json").replace("utr", "otr")
      )
      val errorBody =
        """{ "code": "SERVICE_UNAVAILABLE", "reason": "Dependent systems are currently not responding." }"""
      stubForPost(subscribeUrl, SERVICE_UNAVAILABLE, errorBody)

      val response = await(desConnector.subscribe("Z019283", payload))

      response.status           mustBe SERVICE_UNAVAILABLE
      Json.parse(response.body) mustBe Json.parse(errorBody)
      verifyDesPost(subscribeUrl, payload)
    }

    "Return an exception when the upstream connection fails" in {
      server.stubFor(
        post(urlEqualTo(subscribeUrl))
          .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK))
      )

      intercept[Exception](await(desConnector.subscribe("Z019283", Json.obj())))
    }
  }

  "Registration endpoint" should {
    "Return a status 200 when valid json posted" in {
      val payload      = loadJsonFromResource("/json/registration_example.json")
      val responseJson =
        """{
            |  "safeId": "XE0001234567890",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isEditable": true,
            |  "isAnAgent": false,
            |  "isAnASAgent": false,
            |  "isAnIndividual": true,
            |  "individual": {
            |    "firstName": "Stephen",
            |    "lastName": "Wood",
            |    "dateOfBirth": "1990-04-03"
            |  },
            |  "address": {
            |    "addressLine1": "100 SuttonStreet",
            |    "addressLine2": "Wokingham",
            |    "addressLine3": "Surrey",
            |    "addressLine4": "London",
            |    "postalCode": "DH14EJ",
            |    "countryCode": "GB"
            |  },
            |  "contactDetails": {
            |    "primaryPhoneNumber": "01332752856",
            |    "secondaryPhoneNumber": "07782565326",
            |    "faxNumber": "01332754256",
            |    "emailAddress": "stephen@manncorpone.co.uk"
            |  }
            |}""".stripMargin

      stubForPost(registerUrl, OK, responseJson)

      val response = await(desConnector.register("Z019283", payload))

      response.status           mustBe OK
      Json.parse(response.body) mustBe Json.parse(responseJson)
      verifyDesPost(registerUrl, payload)
    }

    "Return a status 503 when invalid json posted" in {
      val payload   = Json.toJson(
        loadStringFromResource("/json/registration_example.json").replace("utr", "otr")
      )
      val errorBody =
        """{ "code": "SERVICE_UNAVAILABLE", "reason": "Dependent systems are currently not responding." }"""

      stubForPost(registerUrl, SERVICE_UNAVAILABLE, errorBody)

      val response = await(desConnector.register("Z019283", payload))

      response.status           mustBe SERVICE_UNAVAILABLE
      Json.parse(response.body) mustBe Json.parse(errorBody)
      verifyDesPost(registerUrl, payload)
    }

    "Return an exception when the upstream connection fails" in {
      server.stubFor(
        post(urlEqualTo(registerUrl))
          .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK))
      )

      intercept[Exception](await(desConnector.register("Z019283", Json.obj())))
    }
  }

  def verifyDesPost(url: String, expectedBody: JsValue): Unit =
    server.verify(
      postRequestedFor(urlEqualTo(url))
        .withHeader("Environment", equalTo(appConfig.desUrlHeaderEnv))
        .withHeader("Authorization", equalTo(s"Bearer ${appConfig.desAuthToken}"))
        .withHeader("CorrelationId", matching(uuidPattern))
        .withRequestBody(equalToJson(expectedBody.toString))
    )

}
