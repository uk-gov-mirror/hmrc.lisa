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

import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.{HeaderCarrier, RequestId}

import java.util.UUID

class CorrelationGeneratorSpec extends PlaySpec with MockitoSugar {

  private val uuid = UUID.randomUUID().toString

  class TestClass extends CorrelationGenerator

  val testClass: TestClass = new TestClass {
    override def generateRandomUUID: String = uuid
  }

  "addCorrelationId" when {

    "the headerCarrier has no requestId" should {
      "generate a random correlation id and add it as an extra header" in {
        val hc = HeaderCarrier()
        testClass.addCorrelationId(hc) mustBe hc.copy(extraHeaders = Seq("CorrelationId" -> uuid))
      }
    }

    "the headerCarrier has a requestId matching the 8-4-4-4 format" should {
      "build the correlation id from the requestId and the last 12 chars of a fresh UUID" in {
        val requestId = "abcd0000-dh12-fg34-ij56"
        val hc        = HeaderCarrier(requestId = Some(RequestId(requestId)))

        testClass.addCorrelationId(hc) mustBe hc.copy(extraHeaders =
          Seq("CorrelationId" -> s"$requestId-${uuid.substring(24)}")
        )
      }
    }

    "the headerCarrier has a requestId that does not match the 8-4-4-4 format" should {
      "fall back to a freshly generated UUID as the correlation id" in {
        val requestId = "1a2b-dh12-fg34-ij56"
        val hc        = HeaderCarrier(requestId = Some(RequestId(requestId)))

        testClass.addCorrelationId(hc) mustBe hc.copy(extraHeaders = Seq("CorrelationId" -> uuid))
      }
    }

  }

}
