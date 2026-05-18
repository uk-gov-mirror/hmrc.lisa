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
import play.api.http.Status.{ACCEPTED, CREATED}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class LisaRoutingConnector @Inject() (
  config: AppConfig,
  desConnector: DesConnector,
  hipSubscriptionConnector: HipSubscriptionConnector
)(using ec: ExecutionContext) {

  def register(utr: String, payload: JsValue)(using hc: HeaderCarrier): Future[HttpResponse] =
    desConnector.register(utr, payload)

  def subscribe(lisaManagerReferenceNumber: String, payload: JsValue)(using hc: HeaderCarrier): Future[HttpResponse] =
    if (config.useHipSubscription) {
      hipSubscriptionConnector
        .subscribe(lisaManagerReferenceNumber, payload)
        .map(response =>
          response.status match {
            case CREATED =>
              val subscriptionId =
                (response.json \ "success" \ "subscriptionId").as[String]

              HttpResponse(
                status = ACCEPTED,
                body = Json.obj("SubscriptionID" -> subscriptionId).toString,
                headers = response.headers
              )

            case _ =>
              response
          }
        )
    } else {
      desConnector.subscribe(lisaManagerReferenceNumber, payload)
    }

}
