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

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DesConnector @Inject()(config: AppConfig, httpClientV2: HttpClientV2)(implicit ec: ExecutionContext)
  extends RawResponseReads  with Logging with CorrelationGenerator {

  lazy val desUrl = config.desUrl
  lazy val subscriptionUrl = s"$desUrl/lifetime-isa/manager"
  lazy val registrationUrl = s"$desUrl/registration/organisation"

  private val desHeaders: Seq[(String, String)] = Seq(
    "Environment" -> config.desUrlHeaderEnv,
    "Authorization" -> s"Bearer ${config.desAuthToken}"
  )

  def subscribe(lisaManager: String, payload: JsValue)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val uri = s"$subscriptionUrl/$lisaManager/subscription"
    httpPost(uri, payload, "subscribe", "subscription")
  }

  def register(utr: String, payload: JsValue)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val uri = s"$registrationUrl/utr/$utr"
    httpPost(uri, payload, "register", "registerOnce")
  }

  private def httpPost(uri: String, payload: JsValue, urlType: String, connectorLog: String)(implicit hc: HeaderCarrier) = {
    logger.info(s"DES Connector post $connectorLog $uri")
    val headerCarrier = addCorrelationId(hc)
    httpClientV2.post(url"$uri")(headerCarrier).setHeader(desHeaders: _*).withBody(payload).execute recover {
      case e: Exception =>
        logger.error(s"Error in DesConnector $urlType : ${e.getMessage}")
        throw e
    }
  }

}
