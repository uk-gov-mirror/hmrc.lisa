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

import base.BaseTestSpec
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.Status.{ACCEPTED, OK, SERVICE_UNAVAILABLE}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, RequestId, UpstreamErrorResponse}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.Source

class DesConnectorSpec extends BaseTestSpec { // scalastyle:off magic.number
  val uuid = "123e4567-e89b-42d3-a456-556642440000"

  val desConnector = new DesConnector(mockAppConfig, mockHttpClientV2) {
    override def generateRandomUUID: String = uuid
  }

  when(mockAppConfig.desUrl).thenReturn("http://localhost:1234")
  when(mockHttpClientV2.post(any())(any())).thenReturn(mockRequestBuilder)
  when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
  when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)

  "Subscription endpoint" should {
    "Return a status 202" when {
      "Valid json posted" in {

        when(mockRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(
            Future.successful(
              HttpResponse(
                status = ACCEPTED,
                body = s"""{"SubscriptionID": "928282776"}"""
              )
            )
          )

        doSubcribe { response =>
          response.status must be(ACCEPTED)
        }
      }
    }
    "Return a status 503" when {
      "invalid json posted" in {
        when(mockRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(
            Future.successful(
              HttpResponse(
                status = SERVICE_UNAVAILABLE,
                body = s"""
                {
                  "code": "SERVICE_UNAVAILABLE",
                  "reason": "Dependent systems are currently not responding."
                }
                """
              )
            )
          )

        doInvalidSubscribe { response =>
          response.status must be(SERVICE_UNAVAILABLE)
        }
      }
    }
  }

  "Return an exception" when {
    "an invalid status is returned" in {
      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("something failed", 502, 500)))

      intercept[UpstreamErrorResponse](await(desConnector.subscribe("Z019281", Json.obj())))
    }
  }

  "Registration endpoint" should {
    "Return a status 200" when {
      "Valid json posted" in {
        when(mockRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(
            Future.successful(
              HttpResponse(
                status = OK,
                body = s"""{
                        "safeId": "XE0001234567890",
                        "agentReferenceNumber": "AARN1234567",
                        "isEditable": true,
                        "isAnAgent": false,
                        "isAnASAgent": false,
                        "isAnIndividual": true,
                        "individual": {
                          "firstName": "Stephen",
                          "lastName": "Wood",
                          "dateOfBirth": "1990-04-03"
                        },
                        "address": {
                          "addressLine1": "100 SuttonStreet",
                          "addressLine2": "Wokingham",
                          "addressLine3": "Surrey",
                          "addressLine4": "London",
                          "postalCode": "DH14EJ",
                          "countryCode": "GB"
                        },
                        "contactDetails": {
                          "primaryPhoneNumber": "01332752856",
                          "secondaryPhoneNumber": "07782565326",
                          "faxNumber": "01332754256",
                          "emailAddress": "stephen@manncorpone.co.uk"
                        }
                      }""".stripMargin
              )
            )
          )

        doRegister { response =>
          response.status must be(OK)
        }
      }
    }
    "Return a status 503" when {
      "invalid json posted" in {
        when(mockRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(
            Future.successful(
              HttpResponse(
                status = SERVICE_UNAVAILABLE,
                body = s"""
                {
                  "code": "SERVICE_UNAVAILABLE",
                  "reason": "Dependent systems are currently not responding."
                }
                """
              )
            )
          )

        doInvalidRegister { response =>
          response.status must be(SERVICE_UNAVAILABLE)
        }
      }
    }
    "Return an exception" when {
      "an error status is returned" in {
        when(mockRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(Future.failed(UpstreamErrorResponse("something failed", 502, 500)))

        intercept[UpstreamErrorResponse](await(desConnector.register("Z019256", Json.obj())))
      }
    }
  }

  "add correlation id" should {
    "request id is not present in the headerCarrier" when {
      "generate random correlation id" in {
        val hc = HeaderCarrier()
        desConnector.addCorrelationId(hc) mustBe hc.copy(extraHeaders = Seq("CorrelationId" -> uuid))
      }
    }
    "request id in headerCarrier" when {
      "request id matches required format(8-4-4-4)" in {
        val requestId = "abcd0000-dh12-fg34-ij56"
        val hc        = HeaderCarrier(requestId = Some(RequestId(requestId)))
        desConnector.addCorrelationId(hc) mustBe hc.copy(extraHeaders =
          Seq("CorrelationId" -> s"$requestId-${uuid.substring(24)}")
        )
      }

      "request id does not match required format(8-4-4-4)" in {
        val requestId = "1a2b-dh12-fg34-ij56"
        val hc        = HeaderCarrier(requestId = Some(RequestId(requestId)))
        desConnector.addCorrelationId(hc) mustBe hc.copy(extraHeaders = Seq("CorrelationId" -> uuid))
      }
    }
  }

  private def doSubcribe(callback: HttpResponse => Unit): Unit = {
    val jsVal: JsValue =
      Json.toJson(Source.fromInputStream(getClass.getResourceAsStream("/json/subscription_example.json")).mkString)
    val response       = Await.result(desConnector.subscribe("Z019283", jsVal), Duration.Inf)

    callback(response)
  }

  private def doRegister(callback: HttpResponse => Unit): Unit = {
    val jsVal: JsValue =
      Json.toJson(Source.fromInputStream(getClass.getResourceAsStream("/json/registration_example.json")).mkString)
    val response       = Await.result(desConnector.register("Z019283", jsVal), Duration.Inf)

    callback(response)
  }

  private def doInvalidSubscribe(callback: HttpResponse => Unit): Unit = {
    val jsVal: JsValue = Json.toJson(
      Source
        .fromInputStream(getClass.getResourceAsStream("/json/subscription_example.json"))
        .mkString
        .replace("utr", "otr")
    )
    val response       = Await.result(desConnector.subscribe("Z019283", jsVal), Duration.Inf)

    callback(response)
  }

  private def doInvalidRegister(callback: HttpResponse => Unit): Unit = {
    val jsVal: JsValue = Json.toJson(
      Source
        .fromInputStream(getClass.getResourceAsStream("/json/registration_example.json"))
        .mkString
        .replace("utr", "otr")
    )
    val response       = Await.result(desConnector.register("Z019283", jsVal), Duration.Inf)

    callback(response)
  }

}
