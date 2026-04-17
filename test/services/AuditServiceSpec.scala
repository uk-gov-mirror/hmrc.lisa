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

package services

import base.BaseTestSpec
import org.mockito.Mockito.*
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent

class AuditServiceSpec extends BaseTestSpec with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfter {

  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  object SUT extends AuditService(mockAuditConnector, mockAppConfig)

  "AuditService" must {

    before {
      reset(mockAuditConnector)
    }

    "build an audit event with the correct details" in {
      SUT.audit(
        "submitSubscriptionSuccess",
        "submitSubscription",
        Map("safeId" -> "safeId", "lisaManagerRef" -> "lisaManagerRef", "subscriptionId" -> "subscriptionId")
      )

      val captor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])

      verify(mockAuditConnector).sendEvent(captor.capture())(ArgumentMatchers.any(), ArgumentMatchers.any())

      val event = captor.getValue

      event.auditSource mustBe "lisa"
      event.auditType   mustBe "submitSubscriptionSuccess"

      event.tags must contain("path" -> "submitSubscription")
      event.tags must contain("transactionName" -> "submitSubscriptionSuccess")

      event.detail must contain("safeId" -> "safeId")
      event.detail must contain("lisaManagerRef" -> "lisaManagerRef")
      event.detail must contain("subscriptionId" -> "subscriptionId")
    }

  }

}
