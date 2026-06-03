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
import config.AppConfig
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import play.api.http.Status.{CREATED, SERVICE_UNAVAILABLE}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, RequestId}
import utils.ConnectorSpecHelper
import org.mockito.Mockito.{doReturn, spy, verify}

import scala.concurrent.{ExecutionContext, Future}

class HipSubscriptionConnectorSpec extends ConnectorSpecHelper {

  given hc: HeaderCarrier = HeaderCarrier()

  given ec: ExecutionContext = injector.instanceOf[ExecutionContext]

  lazy val hipSubscriptionConnector: HipSubscriptionConnector =
    injector.instanceOf[HipSubscriptionConnector]

  private val subscribeUrl =
    "/RESTAdapter/lisa/subscription/Z019283"

  "HipSubscriptionConnector" should {

    "Return a status 201 when valid json posted" in {
      val payload      = loadJsonFromResource("/json/subscription_example.json")
      val responseJson =
        """{
          |  "success": {
          |    "subscriptionId": "928282776"
          |  }
          |}""".stripMargin

      stubForPost(subscribeUrl, CREATED, responseJson)

      val response =
        await(hipSubscriptionConnector.subscribe("Z019283", payload))

      response.status           mustBe CREATED
      Json.parse(response.body) mustBe Json.parse(responseJson)
      verifyHipPost(subscribeUrl, payload)
    }

    "Return a status 503 when invalid json posted" in {
      val payload =
        Json.toJson(
          loadStringFromResource("/json/subscription_example.json").replace("utr", "otr")
        )

      val errorBody =
        """{
          |  "errors": {
          |    "processingDate": "2026-03-10T12:34:46Z",
          |    "code": "500",
          |    "text": "Dependent systems are currently not responding."
          |  }
          |}""".stripMargin

      stubForPost(subscribeUrl, SERVICE_UNAVAILABLE, errorBody)

      val response =
        await(hipSubscriptionConnector.subscribe("Z019283", payload))

      response.status           mustBe SERVICE_UNAVAILABLE
      Json.parse(response.body) mustBe Json.parse(errorBody)
      verifyHipPost(subscribeUrl, payload)
    }

    "Return an exception when the upstream connection fails" in {
      server.stubFor(
        post(urlEqualTo(subscribeUrl))
          .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK))
      )

      intercept[Exception](
        await(hipSubscriptionConnector.subscribe("Z019283", Json.obj()))
      )
    }

    "fallback to generateCorrelationId when correlationid header is missing" in {
      val config         = mock[AppConfig]
      val httpClientV2   = mock[HttpClientV2]
      val requestBuilder = mock[RequestBuilder]

      when(config.hipUrl).thenReturn("http://localhost:8885")

      class TestHipSubscriptionConnector(config: AppConfig, httpClientV2: HttpClientV2)(using ec: ExecutionContext)
          extends HipSubscriptionConnector(config, httpClientV2) {

        override def addCorrelationId(hc: HeaderCarrier): HeaderCarrier = hc

        override def generateCorrelationId(requestId: Option[RequestId]): String =
          "fallback-correlation-id"
      }

      val actualConnector = new TestHipSubscriptionConnector(config, httpClientV2)
      val connector       = spy(actualConnector)

      doReturn("fallback-correlation-id")
        .when(connector)
        .generateCorrelationId(any())

      val lisaManagerReferenceNumber = "Z123456"

      val payload =
        Json.obj("foo" -> "bar")

      given HeaderCarrier =
        HeaderCarrier(
          requestId = Some(RequestId("12345678-1234-5678-abcd-999999999999"))
        )

      val expectedResponse =
        HttpResponse(201, """{"success":{"subscriptionId":"123"}}""")

      when(
        httpClientV2.post(any())(using any[HeaderCarrier])
      ).thenReturn(requestBuilder)

      when(
        requestBuilder.setHeader(any[Seq[(String, String)]]: _*)
      ).thenReturn(requestBuilder)

      when(
        requestBuilder.withBody(eqTo(payload))(using any(), any(), any())
      ).thenReturn(requestBuilder)

      when(
        requestBuilder.execute[HttpResponse](using any(), any())
      ).thenReturn(Future.successful(expectedResponse))

      val result =
        connector.subscribe(lisaManagerReferenceNumber, payload).futureValue

      result mustBe expectedResponse
      verify(connector).generateCorrelationId(
        Some(RequestId("12345678-1234-5678-abcd-999999999999"))
      )
    }
  }

  def verifyHipPost(url: String, expectedBody: JsValue): Unit =
    server.verify(
      postRequestedFor(urlEqualTo(url))
        .withHeader(hipSubscriptionConnector.CorrelationIdHeaderName, matching(uuidPattern))
        .withHeader("X-Originating-System", equalTo("LISA"))
        .withHeader("X-Receipt-Date", matching(".+"))
        .withHeader("X-Transmitting-System", equalTo("HIP"))
        .withHeader("Authorization", equalTo("Basic dGVzdElkOnRlc3RTZWNyZXQ="))
        .withRequestBody(equalToJson(expectedBody.toString))
    )

}
