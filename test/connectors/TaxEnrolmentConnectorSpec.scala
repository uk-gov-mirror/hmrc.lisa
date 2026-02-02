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

import helpers.BaseTestSpec
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR, NO_CONTENT}
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class TaxEnrolmentConnectorSpec extends BaseTestSpec {

  val taxEnrolmentConnector = new TaxEnrolmentConnector(mockAppConfig, mockHttpClientV2)

  when(mockAppConfig.taxEnrolmentUrl).thenReturn("http://localhost:1234")

  "Get enrolment status" should {
    when(mockHttpClientV2.get(any())(any())).thenReturn(mockRequestBuilder)

    "return a success verbatim" when {
      "a successful response is returned from tax enrolment" in {
        when(mockRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(
            Future.successful(
              HttpResponse(
                status = ACCEPTED,
                body = s"""{"status": "PENDING"}"""
              )
            )
          )

        doEnrolmentStatus { response =>
          response.status             must be(ACCEPTED)
          Json.parse(response.body) mustBe Json.parse(s"""{"status": "PENDING"}""")
        }
      }
    }
    "return an error verbatim" when {
      "an error is returned from tax enrolment" in {
        when(mockHttpClientV2.get(any())(any()).execute[HttpResponse](any(), any()))
          .thenReturn(
            Future.successful(
              HttpResponse(
                status = INTERNAL_SERVER_ERROR,
                body = s"""{"code": "INTERNAL_ERROR"}"""
              )
            )
          )

        doEnrolmentStatus { response =>
          response.status             must be(INTERNAL_SERVER_ERROR)
          Json.parse(response.body) mustBe Json.parse(s"""{"code": "INTERNAL_ERROR"}""")
        }
      }
    }
  }

  "Subscribe" should {
    when(mockHttpClientV2.put(any())(any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)

    "return a success verbatim" when {
      "a successful response is returned from tax enrolment" in {
        when(mockRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(
            Future.successful(
              HttpResponse(
                status = NO_CONTENT,
                body = ""
              )
            )
          )

        doSubscribe { response =>
          response.status must be(NO_CONTENT)
          response.body mustBe ""
        }
      }
    }
    "return an error verbatim" when {
      "an error is returned from tax enrolment" in {
        when(mockRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(
            Future.successful(
              HttpResponse(
                status = INTERNAL_SERVER_ERROR,
                body = s"""{"code": "INTERNAL_ERROR"}"""
              )
            )
          )

        doSubscribe { response =>
          response.status             must be(INTERNAL_SERVER_ERROR)
          Json.parse(response.body) mustBe Json.parse(s"""{"code": "INTERNAL_ERROR"}""")
        }
      }
    }
  }

  private def doEnrolmentStatus(callback: HttpResponse => Unit): Unit = {
    val response = Await.result(taxEnrolmentConnector.enrolmentStatus("Z0192"), Duration.Inf)

    callback(response)
  }

  private def doSubscribe(callback: HttpResponse => Unit): Unit = {
    val response = Await.result(taxEnrolmentConnector.subscribe("1234567890", Json.parse("{}")), Duration.Inf)

    callback(response)
  }

}
