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

import config.AppConfig
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import play.api.Configuration
import play.api.http.Status.{ACCEPTED, CREATED, INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.Json
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import utils.ConnectorSpecHelper

import scala.concurrent.{ExecutionContext, Future}

class LisaRoutingConnectorSpec extends ConnectorSpecHelper with BeforeAndAfterEach {

  given hc: HeaderCarrier    = HeaderCarrier()
  given ec: ExecutionContext = injector.instanceOf[ExecutionContext]

  private val desConnector             = mock[DesConnector]
  private val hipSubscriptionConnector = mock[HipSubscriptionConnector]

  private val lisaManagerReferenceNumber = "Z019283"
  private val utr                        = "1234567890"
  private val payload                    = Json.obj("test" -> "payload")

  override def beforeEach(): Unit = {
    reset(desConnector, hipSubscriptionConnector)
    super.beforeEach()
  }

  private def appConfig(useHipSubscription: Boolean): AppConfig =
    new AppConfig(
      Configuration(
        "desauthtoken"                              -> "9999999",
        "environment"                               -> "local",
        "rosmCallbackUrl"                           -> "http://localhost:8886/lisa/rosm/callback",
        "appName"                                   -> "lisa",
        "microservice.services.des.protocol"        -> "http",
        "microservice.services.des.host"            -> "localhost",
        "microservice.services.des.port"            -> 8885,
        "microservice.services.hip.protocol"        -> "http",
        "microservice.services.hip.host"            -> "localhost",
        "microservice.services.hip.port"            -> 8885,
        "microservice.services.tax-enrolments.host" -> "localhost",
        "microservice.services.tax-enrolments.port" -> 8885,
        "features.hip.subscription"                 -> useHipSubscription
      )
    )

  "subscribe" should {

    "delegate to HipSubscriptionConnector when HIP subscription feature flag is enabled" in {
      val hipResponse =
        HttpResponse(CREATED, """{"success":{"subscriptionId":"928282776"}}""")

      when(
        hipSubscriptionConnector.subscribe(eqTo(lisaManagerReferenceNumber), eqTo(payload))(using any[HeaderCarrier])
      ).thenReturn(Future.successful(hipResponse))

      val connector =
        new LisaRoutingConnector(
          appConfig(useHipSubscription = true),
          desConnector,
          hipSubscriptionConnector
        )

      val actual   = await(connector.subscribe(lisaManagerReferenceNumber, payload))
      val expected = HttpResponse(ACCEPTED, """{"SubscriptionID":"928282776"}""")

      actual.status           mustBe expected.status
      Json.parse(actual.body) mustBe Json.parse(expected.body)

      verify(hipSubscriptionConnector)
        .subscribe(eqTo(lisaManagerReferenceNumber), eqTo(payload))(using any[HeaderCarrier])

      verify(desConnector, never())
        .subscribe(any[String], any())(using any[HeaderCarrier])
    }

    "delegate to HipSubscriptionConnector when HIP subscription feature flag is enabled and subscription fails" in {
      val hipResponse =
        HttpResponse(
          status = INTERNAL_SERVER_ERROR,
          body = """{
              |  "error": {
              |    "code": "500",
              |    "message": "boom",
              |    "logID": "D82EBAB67AC6D7565C0682CA91BDC577"
              |  }
              |}""".stripMargin
        )

      when(
        hipSubscriptionConnector.subscribe(eqTo(lisaManagerReferenceNumber), eqTo(payload))(using any[HeaderCarrier])
      ).thenReturn(Future.successful(hipResponse))

      val connector =
        new LisaRoutingConnector(
          appConfig(useHipSubscription = true),
          desConnector,
          hipSubscriptionConnector
        )

      val actual = await(connector.subscribe(lisaManagerReferenceNumber, payload))
      actual mustBe hipResponse

      verify(hipSubscriptionConnector)
        .subscribe(eqTo(lisaManagerReferenceNumber), eqTo(payload))(using any[HeaderCarrier])

      verify(desConnector, never())
        .subscribe(any[String], any())(using any[HeaderCarrier])
    }

    "delegate to DesConnector when HIP subscription feature flag is disabled" in {
      val desResponse =
        HttpResponse(ACCEPTED, """{"SubscriptionID":"928282776"}""")

      when(
        desConnector.subscribe(eqTo(lisaManagerReferenceNumber), eqTo(payload))(using any[HeaderCarrier])
      ).thenReturn(Future.successful(desResponse))

      val connector =
        new LisaRoutingConnector(
          appConfig(useHipSubscription = false),
          desConnector,
          hipSubscriptionConnector
        )

      val response =
        await(connector.subscribe(lisaManagerReferenceNumber, payload))

      response mustBe desResponse

      verify(desConnector)
        .subscribe(eqTo(lisaManagerReferenceNumber), eqTo(payload))(using any[HeaderCarrier])

      verify(hipSubscriptionConnector, never())
        .subscribe(any[String], any())(using any[HeaderCarrier])
    }
  }

  "register" should {

    "always delegate to DesConnector" in {
      val desResponse =
        HttpResponse(OK, """{"safeId":"XE0001234567890"}""")

      when(
        desConnector.register(eqTo(utr), eqTo(payload))(using any[HeaderCarrier])
      ).thenReturn(Future.successful(desResponse))

      val connector =
        new LisaRoutingConnector(
          appConfig(useHipSubscription = true),
          desConnector,
          hipSubscriptionConnector
        )

      val response =
        await(connector.register(utr, payload))

      response mustBe desResponse

      verify(desConnector)
        .register(eqTo(utr), eqTo(payload))(using any[HeaderCarrier])

      verifyNoInteractions(hipSubscriptionConnector)
    }
  }

}
