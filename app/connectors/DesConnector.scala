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

class DesConnector @Inject() (config: AppConfig, httpClientV2: HttpClientV2)(using ec: ExecutionContext)
    extends RawResponseReads with Logging with CorrelationGenerator {

  lazy val desUrl: String  = config.desUrl
  lazy val subscriptionUrl = s"$desUrl/lifetime-isa/manager"
  lazy val registrationUrl = s"$desUrl/registration/organisation"

  private val desHeaders: Seq[(String, String)] = Seq(
    "Environment"   -> config.desUrlHeaderEnv,
    "Authorization" -> s"Bearer ${config.desAuthToken}"
  )

  def subscribe(lisaManager: String, payload: JsValue)(using hc: HeaderCarrier): Future[HttpResponse] = {
    val uri = s"$subscriptionUrl/$lisaManager/subscription"
    httpPost(uri, payload, "subscribe")
  }

  def register(utr: String, payload: JsValue)(using hc: HeaderCarrier): Future[HttpResponse] = {
    val uri = s"$registrationUrl/utr/$utr"
    httpPost(uri, payload, "register")
  }

  private def httpPost(uri: String, payload: JsValue, connectorLog: String)(using
    hc: HeaderCarrier
  ) = {
    logger.info(s"DES Connector post $connectorLog $uri")
    val headerCarrier = addCorrelationId(hc)

    httpClientV2
      .post(url"$uri")(headerCarrier)
      .setHeader(desHeaders*)
      .withBody(payload)
      .execute
  }

}
