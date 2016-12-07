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

  // This feeder is used for generating large contents for requests.
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

  // FIXME: generate 1MB of contents for the attachment
  final val RequestWithAttachmentScenario = scenario("X-Road request with MTOM attachment")
    .feed(MessageIdFeeder)
    .exec(
      http("X-Road request with MTOM attachment")
        .post(SimulationUtil.securityServerUrl())
        .header("Content-Type",
          "multipart/related; type=\"application/xop+xml\"; start=\"<rootpart@soapui.org>\"; start-info=\"text/xml\"; boundary=\"----=_Part_10_1777426800.1476273281168\"")
        .body(StringBody(SimulationRequests.XRoadRequestWithMTOMAttachment))
        .check(regex(SoapFaultRegexp).notExists)
    )
}
