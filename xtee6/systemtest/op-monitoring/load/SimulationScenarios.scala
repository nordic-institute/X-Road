// The MIT License
// Copyright (c) 2016 Estonian Information System Authority (RIA), Population Register Centre (VRK)
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package opmonitor.loadtesting;

import io.gatling.core.Predef._ 
import io.gatling.http.Predef._

// Scenario definitions for the load tests of operational monitoring.
object SimulationScenarios {

  // We'll generate and store a unique message ID for each X-Road request
  // sent during the simulation.
  // The message_id_placeholder variable will be replaced with the generated
  // value by the feeder mechanism.
  private final val MessageIdFeeder = Iterator.continually(
    Map(
      "message_id_placeholder" -> SimulationUtil.generateAndStoreRandomMessageId()
    )
  )

  // This feeder is used for generating large contents for requests or their
  // attachments.
  private final val MessageContentsFeeder = Iterator.continually(
    Map(
      "random_1_mb_contents" -> SimulationUtil.generateRandom1MbContents()
    )
  )

  // Match the soap fault tag in a case-insensitive way.
  private final val SoapFaultRegexp = "(?i)(SOAP-ENV:Fault)";

  final val SimpleRequestScenario = scenario("Simple X-Road request")
    .feed(MessageIdFeeder)
    .exec(
      http("Simple X-Road request")
        .post(SimulationUtil.securityServerUrl())
        .header("Content-Type", "text/xml")
        .body(StringBody(SimulationRequests.SimpleXRoadRequest))
        .check(regex(SoapFaultRegexp).notExists)
      )

  final val RequestWith1MbBodyScenario = scenario("X-Road request with 1MB body")
    .feed(MessageIdFeeder)
    .feed(MessageContentsFeeder)
    .exec(
      http("X-Road request with 1MB body")
        .post(SimulationUtil.securityServerUrl())
        .header("Content-Type", "text/xml")
        .body(StringBody(SimulationRequests.XRoadRequestWith1MbBody))
        .check(regex(SoapFaultRegexp).notExists)
      )

  final val RequestWithAttachmentScenario = scenario("X-Road request with MTOM attachment")
    .feed(MessageIdFeeder)
    .feed(MessageContentsFeeder)
    .exec(
      http("X-Road request with MTOM attachment")
        .post(SimulationUtil.securityServerUrl())
        .header("Content-Type",
          "multipart/related; type=\"application/xop+xml\"; start=\"<rootpart@soapui.org>\"; start-info=\"text/xml\"; boundary=\"----=_Part_10_1777426800.1476273281168\"")
        .body(StringBody(SimulationRequests.XRoadRequestWithMTOMAttachment))
        .check(regex(SoapFaultRegexp).notExists)
    )
}
