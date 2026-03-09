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

package base

import config.AppConfig
import connectors.{DesConnector, TaxEnrolmentConnector}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.stubControllerComponents
import services.AuditService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

trait BaseTestSpec extends PlaySpec with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfterEach {

  lazy val mockDesConnector: DesConnector                   = mock[DesConnector]
  lazy val mockTaxEnrolmentConnector: TaxEnrolmentConnector = mock[TaxEnrolmentConnector]
  lazy val mockAuthCon: AuthConnector                       = mock[AuthConnector]
  lazy val controllerComponents: ControllerComponents       = stubControllerComponents()
  lazy val mockAppConfig: AppConfig                         = mock[AppConfig]
  lazy val mockHttpClientV2: HttpClientV2                   = mock[HttpClientV2]
  lazy val mockRequestBuilder: RequestBuilder               = mock[RequestBuilder]
  lazy val mockAuditService: AuditService                   = mock[AuditService]

  implicit def hc: HeaderCarrier = HeaderCarrier()

  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

}
