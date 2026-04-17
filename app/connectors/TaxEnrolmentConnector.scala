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
import play.api.Logging
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TaxEnrolmentConnector @Inject() (config: AppConfig, httpClientV2: HttpClientV2)(using ec: ExecutionContext)
    extends RawResponseReads with Logging with CorrelationGenerator {

  lazy val taxEnrolmentUrl: String = config.taxEnrolmentUrl

  def enrolmentStatus(groupId: String)(using hc: HeaderCarrier): Future[HttpResponse] = {
    val uri = s"$taxEnrolmentUrl/groups/$groupId/subscriptions"
    logger.info(s"Tax Enrolment connector get subscriptions $uri")
    httpClientV2.get(url"$uri")(addCorrelationId(hc)).execute
  }

  def subscribe(subscriptionId: String, body: JsValue)(using hc: HeaderCarrier): Future[HttpResponse] = {
    val uri = s"$taxEnrolmentUrl/subscriptions/$subscriptionId/subscriber"
    logger.info(s"Tax Enrolment connector put subscribe $uri")
    httpClientV2.put(url"$uri")(addCorrelationId(hc)).withBody(body).execute
  }

}
