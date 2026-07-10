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
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import sttp.model.HeaderNames
import java.util.Base64
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HipSubscriptionConnector @Inject() (
  config: AppConfig,
  httpClientV2: HttpClientV2
)(using ec: ExecutionContext)
    extends RawResponseReads with Logging with CorrelationGenerator {

  private lazy val hipUrl: String =
    config.hipUrl

  private def authSecret: String =
    Base64.getEncoder
      .encodeToString(
        s"${config.hipClientId}:${config.hipClientSecret}"
          .getBytes(StandardCharsets.UTF_8)
      )

  private def hipHeaders: Seq[(String, String)] =
    Seq(
      HeaderNames.Authorization -> s"Basic $authSecret",
      "X-Originating-System"    -> "LISA",
      "X-Receipt-Date"          -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
      "X-Transmitting-System"   -> "HIP"
    )

  def subscribe(lisaManagerReferenceNumber: String, payload: JsValue)(using hc: HeaderCarrier): Future[HttpResponse] = {
    val uri =
      s"$hipUrl/etmp/RESTAdapter/lisa/subscription/$lisaManagerReferenceNumber"

    logger.info(s"HIP Connector post subscribe $uri")

    val correlationId =
      addCorrelationId(hc).extraHeaders
        .map { case (key, value) => (key.toLowerCase, value) }
        .collectFirst { case ("correlationid", value) =>
          value
        }
        .getOrElse(generateCorrelationId(hc.requestId))

    httpClientV2
      .post(url"$uri")(hc)
      .setHeader((CorrelationIdHeaderName, correlationId) +: hipHeaders*)
      .withBody(payload)
      .execute
  }

}
