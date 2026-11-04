/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.e2e.parity.acl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictClassifierTest {

    private static final Map<String, String> MARKED = Map.of("Mock-Response", "true");

    private static Reply fault(String code) {
        return new Reply(500, Map.of("X-Road-Error", code), "{}");
    }

    @Test
    void successWithBackendMarkerIsAllowed() {
        assertThat(VerdictClassifier.classify(new Reply(200, MARKED, "{}"))).isEqualTo(Verdict.ALLOWED);
        assertThat(VerdictClassifier.classify(new Reply(204, MARKED, ""))).isEqualTo(Verdict.ALLOWED);
    }

    @Test
    void successWithoutBackendMarkerIsError() {
        assertThat(VerdictClassifier.classify(new Reply(200, Map.of(), "{}"))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classify(new Reply(200, Map.of("mock-response", "false"), "{}")))
                .isEqualTo(Verdict.ERROR);
    }

    @Test
    void accessDeniedFaultIsAccessDenied() {
        assertThat(VerdictClassifier.classify(fault("server.serverproxy.access_denied")))
                .isEqualTo(Verdict.ACCESS_DENIED);
    }

    @Test
    void namedFaultsMapToThemselves() {
        assertThat(VerdictClassifier.classify(fault("server.serverproxy.unknown_service")))
                .isEqualTo(Verdict.UNKNOWN_SERVICE);
        assertThat(VerdictClassifier.classify(fault("server.serverproxy.service_disabled")))
                .isEqualTo(Verdict.SERVICE_DISABLED);
        assertThat(VerdictClassifier.classify(fault("server.serverproxy.service_type")))
                .isEqualTo(Verdict.INVALID_SERVICE_TYPE);
        assertThat(VerdictClassifier.classify(fault("server.serverproxy.unknown_member")))
                .isEqualTo(Verdict.UNKNOWN_MEMBER);
        assertThat(VerdictClassifier.classify(fault("server.serverproxy.invalid_security_server")))
                .isEqualTo(Verdict.INVALID_SECURITY_SERVER);
    }

    @Test
    void clientProxyUnknownMemberFaultIsUnknownMember() {
        assertThat(VerdictClassifier.classify(fault("server.clientproxy.unknown_member")))
                .isEqualTo(Verdict.UNKNOWN_MEMBER);
        assertThat(VerdictClassifier.classifySoap(
                new Reply(500, Map.of(), soapFault("server.clientproxy.unknown_member"))))
                .isEqualTo(Verdict.UNKNOWN_MEMBER);
    }

    @Test
    void internalErrorFaultIsInternalError() {
        assertThat(VerdictClassifier.classify(fault("server.serverproxy.internal_error")))
                .isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(VerdictClassifier.classify(fault("server.clientproxy.internal_error"))).isEqualTo(Verdict.ERROR);
    }

    @Test
    void rejectedAgreementTokenFaultIsAgreementTokenRejected() {
        assertThat(VerdictClassifier.classify(fault("server.serverproxy.agreement_token_rejected")))
                .isEqualTo(Verdict.AGREEMENT_TOKEN_REJECTED);
        assertThat(VerdictClassifier.classifySoap(
                new Reply(500, Map.of(), soapFault("server.serverproxy.agreement_token_rejected"))))
                .isEqualTo(Verdict.AGREEMENT_TOKEN_REJECTED);
        assertThat(meta(fault("server.serverproxy.agreement_token_rejected"), List.of(), List.of()))
                .isEqualTo(Verdict.AGREEMENT_TOKEN_REJECTED);
    }

    @Test
    void clientProxyAccessDeniedFaultIsAccessDenied() {
        assertThat(VerdictClassifier.classify(fault("server.clientproxy.access_denied")))
                .isEqualTo(Verdict.ACCESS_DENIED);
        assertThat(VerdictClassifier.classifySoap(
                new Reply(500, Map.of(), soapFault("server.clientproxy.access_denied"))))
                .isEqualTo(Verdict.ACCESS_DENIED);
    }

    @Test
    void clientProxyDataSpaceFailureFaultsAreConsumerFailed() {
        assertThat(VerdictClassifier.classify(fault("server.clientproxy.io_error")))
                .isEqualTo(Verdict.CONSUMER_FAILED);
        assertThat(VerdictClassifier.classify(fault("server.clientproxy.service_failed")))
                .isEqualTo(Verdict.CONSUMER_FAILED);
        assertThat(VerdictClassifier.classifySoap(
                new Reply(500, Map.of(), soapFault("server.clientproxy.service_failed"))))
                .isEqualTo(Verdict.CONSUMER_FAILED);
    }

    @Test
    void badRequestWithoutAnXRoadErrorIsRejectedByTheConsumer() {
        assertThat(VerdictClassifier.classify(new Reply(400, Map.of("Content-Type", "text/html"), "<html/>")))
                .isEqualTo(Verdict.CONSUMER_REJECTED);
    }

    @Test
    void otherClientErrorsWithoutAnXRoadErrorAreError() {
        assertThat(VerdictClassifier.classify(new Reply(403, Map.of(), ""))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classify(new Reply(404, Map.of(), ""))).isEqualTo(Verdict.ERROR);
    }

    @Test
    void unexpectedResponsesAreError() {
        assertThat(VerdictClassifier.classify(null)).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classify(fault("server.serverproxy.something_new"))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classify(fault("access_denied"))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classify(new Reply(500, Map.of(), "boom"))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classify(new Reply(400, Map.of("x-road-error", "server.serverproxy.access_denied"), "")))
                .isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classify(new Reply(302, MARKED, ""))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classify(new Reply(500, MARKED, ""))).isEqualTo(Verdict.ERROR);
    }

    @Test
    void faultHeaderOnAStatusOtherThan500IsError() {
        var denied = Map.of("X-Road-Error", "server.serverproxy.access_denied");

        assertThat(VerdictClassifier.classify(new Reply(501, denied, ""))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classify(new Reply(503, denied, ""))).isEqualTo(Verdict.ERROR);
    }

    private static final String SOAP_OK = "<Body xmlns=\"http://schemas.xmlsoap.org/soap/envelope/\">acl-parity-backend</Body>";

    private static String soapFault(String code) {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><soap:Fault>"
                + "<faultcode>" + code + "</faultcode><faultstring>refused</faultstring></soap:Fault></soap:Body>"
                + "</soap:Envelope>";
    }

    @Test
    void soapSuccessWithBodyMarkerIsAllowed() {
        assertThat(VerdictClassifier.classifySoap(new Reply(200, Map.of(), SOAP_OK))).isEqualTo(Verdict.ALLOWED);
    }

    @Test
    void soapSuccessWithoutBodyMarkerIsError() {
        assertThat(VerdictClassifier.classifySoap(new Reply(200, Map.of(), "<Body>ok</Body>")))
                .isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classifySoap(new Reply(200, MARKED, ""))).isEqualTo(Verdict.ERROR);
    }

    @Test
    void soapMarkerWithoutSuccessStatusIsError() {
        assertThat(VerdictClassifier.classifySoap(new Reply(500, Map.of(), SOAP_OK))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classifySoap(new Reply(302, Map.of(), SOAP_OK))).isEqualTo(Verdict.ERROR);
    }

    @Test
    void soapFaultDecidesTheVerdictOnStatus200AndOn500() {
        var denied = soapFault("server.serverproxy.access_denied");

        assertThat(VerdictClassifier.classifySoap(new Reply(200, Map.of(), denied))).isEqualTo(Verdict.ACCESS_DENIED);
        assertThat(VerdictClassifier.classifySoap(new Reply(500, Map.of(), denied))).isEqualTo(Verdict.ACCESS_DENIED);
        assertThat(VerdictClassifier.classifySoap(new Reply(200, Map.of(), soapFault("server.serverproxy.service_type"))))
                .isEqualTo(Verdict.INVALID_SERVICE_TYPE);
    }

    @Test
    void soapFaultBeatsTheBodyMarker() {
        var body = soapFault("server.serverproxy.access_denied") + SOAP_OK;

        assertThat(VerdictClassifier.classifySoap(new Reply(200, Map.of(), body))).isEqualTo(Verdict.ACCESS_DENIED);
    }

    @Test
    void soapClientProxyRefusalOfTheSecurityServerHintIsInvalidSecurityServer() {
        assertThat(VerdictClassifier.classifySoap(
                new Reply(200, Map.of(), soapFault("server.clientproxy.invalid_security_server"))))
                .isEqualTo(Verdict.INVALID_SECURITY_SERVER);
    }

    @Test
    void soapFaultCodeToleratesPrefixAndPadding() {
        var body = "<s:Fault><s:faultcode xmlns:x=\"y\">  server.serverproxy.unknown_service \n</s:faultcode></s:Fault>";

        assertThat(VerdictClassifier.classifySoap(new Reply(200, Map.of(), body))).isEqualTo(Verdict.UNKNOWN_SERVICE);
    }

    @Test
    void soapUnknownFaultNullAndUnparseableAreError() {
        assertThat(VerdictClassifier.classifySoap(null)).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classifySoap(new Reply(200, Map.of(), soapFault("server.serverproxy.something_new"))))
                .isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classifySoap(new Reply(200, Map.of(), null))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classifySoap(new Reply(200, Map.of(), "not xml at all"))).isEqualTo(Verdict.ERROR);
    }

    @Test
    void describeCarriesTheBodyOfADisabledServiceFaultOnly() {
        var disabled = new Reply(500, Map.of("X-Road-Error", "server.serverproxy.service_disabled"),
                "{\"message\":\"back at noon\"}");
        var denied = new Reply(500, Map.of("X-Road-Error", "server.serverproxy.access_denied"),
                "{\"message\":\"back at noon\"}");

        assertThat(VerdictClassifier.describe(disabled)).contains("back at noon");
        assertThat(VerdictClassifier.describe(denied)).doesNotContain("back at noon");
    }

    @Test
    void describeCarriesTheStartOfTheBodyOfARejectedAgreementToken() {
        var rejected = new Reply(500, Map.of("X-Road-Error", "server.serverproxy.agreement_token_rejected"),
                "{\"message\":\"AGREEMENT_TOKEN_REQUIRED\"}");

        assertThat(VerdictClassifier.describe(rejected)).contains("AGREEMENT_TOKEN_REQUIRED");
    }

    private static final String LIST_JSON = "{\"service\":[{\"service_code\":\"p-v91\"},{\"service_code\":\"p-v91b\"}]}";

    private static Verdict meta(Reply reply, List<String> required, List<String> forbidden) {
        return VerdictClassifier.classifyMetaservice(reply, required, forbidden);
    }

    @Test
    void metaserviceListWithEveryRequiredAndNoForbiddenTextIsAllowed() {
        assertThat(meta(new Reply(200, Map.of(), LIST_JSON), List.of("p-v91", "p-v91b"), List.of("p-v92")))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(meta(new Reply(200, Map.of(), "{}"), List.of(), List.of())).isEqualTo(Verdict.ALLOWED);
    }

    @Test
    void metaserviceListMissingARequiredTextIsError() {
        assertThat(meta(new Reply(200, Map.of(), LIST_JSON), List.of("p-v91", "p-v92"), List.of()))
                .isEqualTo(Verdict.ERROR);
    }

    @Test
    void metaserviceListCarryingAForbiddenTextIsError() {
        assertThat(meta(new Reply(200, Map.of(), LIST_JSON), List.of("p-v91"), List.of("p-v91b")))
                .isEqualTo(Verdict.ERROR);
    }

    @Test
    void metaserviceTextsMatchAsWholeTokens() {
        var body = "{\"service\":[{\"service_code\":\"p-v91-2\"}]}";

        assertThat(meta(new Reply(200, Map.of(), body), List.of("p-v91"), List.of())).isEqualTo(Verdict.ERROR);
        assertThat(meta(new Reply(200, Map.of(), body), List.of(), List.of("p-v91"))).isEqualTo(Verdict.ALLOWED);
        assertThat(meta(new Reply(200, Map.of(), body), List.of("p-v91-2"), List.of("p-v91")))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(meta(new Reply(200, Map.of(), "<a>p-v91</a>"), List.of("p-v91"), List.of()))
                .isEqualTo(Verdict.ALLOWED);
    }

    private static final String SOAP_LIST_METHODS_REPLY = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:id="http://x-road.eu/xsd/identifiers">
                <soapenv:Header>
                    <xro:id>acl-parity-1</xro:id>
                    <xro:protocolVersion>4.0</xro:protocolVersion>
                </soapenv:Header>
                <soapenv:Body>
                    <xro:listMethodsResponse>
                        <xro:service id:objectType="SERVICE">
                            <id:xRoadInstance>DEV</id:xRoadInstance>
                            <id:memberClass>COM</id:memberClass>
                            <id:memberCode>1234</id:memberCode>
                            <id:subsystemCode>PROVIDER</id:subsystemCode>
                            <id:serviceCode>p-v90</id:serviceCode>
                            <id:serviceVersion>v1</id:serviceVersion>
                        </xro:service>
                    </xro:listMethodsResponse>
                </soapenv:Body>
            </soapenv:Envelope>
            """;

    private static final String SOAP_GET_WSDL_REPLY = """
            --jetty123
            Content-Type: text/xml; charset=UTF-8

            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:xro="http://x-road.eu/xsd/xroad.xsd">
                <soapenv:Body><xro:getWsdlResponse/></soapenv:Body>
            </soapenv:Envelope>
            --jetty123
            Content-Type: text/xml
            Content-Transfer-Encoding: binary

            <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                    xmlns:soap12="http://schemas.xmlsoap.org/wsdl/soap12/">
                <wsdl:service name="ProducerService">
                    <wsdl:port name="ProducerPort" binding="tns:ProducerBinding">
                        <soap12:address location="http://example.org/xroad-endpoint"/>
                    </wsdl:port>
                </wsdl:service>
            </wsdl:definitions>
            --jetty123--
            """;

    @Test
    void metaserviceSoapListMethodsReplyIsAllowedWhenItListsTheRequiredServices() {
        var reply = new Reply(200, Map.of(), SOAP_LIST_METHODS_REPLY);

        assertThat(meta(reply, List.of("p-v90"), List.of("p-v91"))).isEqualTo(Verdict.ALLOWED);
        assertThat(meta(reply, List.of("p-v91"), List.of())).isEqualTo(Verdict.ERROR);
        assertThat(meta(reply, List.of(), List.of("p-v90"))).isEqualTo(Verdict.ERROR);
    }

    @Test
    void metaserviceSoapGetWsdlReplyIsAllowedOnlyWithTheMaskedAddress() {
        var reply = new Reply(200, Map.of(), SOAP_GET_WSDL_REPLY);

        assertThat(meta(reply, List.of("example.org/xroad-endpoint"), List.of("isrest:8080")))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(meta(reply, List.of(), List.of("example.org/xroad-endpoint"))).isEqualTo(Verdict.ERROR);
        assertThat(meta(reply, List.of("isrest:8080"), List.of())).isEqualTo(Verdict.ERROR);
    }

    @Test
    void metaserviceSoapFaultDecidesTheVerdictBeforeTheTexts() {
        var body = soapFault("server.serverproxy.service_type");

        assertThat(meta(new Reply(500, Map.of(), body), List.of(), List.of())).isEqualTo(Verdict.INVALID_SERVICE_TYPE);
        assertThat(meta(new Reply(200, Map.of(), body), List.of(), List.of())).isEqualTo(Verdict.INVALID_SERVICE_TYPE);
    }

    @Test
    void metaserviceRestFaultHeaderDecidesTheVerdict() {
        assertThat(meta(fault("server.serverproxy.unknown_service"), List.of(), List.of()))
                .isEqualTo(Verdict.UNKNOWN_SERVICE);
        assertThat(meta(fault("server.serverproxy.something_new"), List.of(), List.of())).isEqualTo(Verdict.ERROR);
    }

    @Test
    void metaserviceUnmappedFaultCodeInTheBodyIsError() {
        var body = soapFault("server.serverproxy.something_new");

        assertThat(meta(new Reply(500, Map.of(), body), List.of(), List.of())).isEqualTo(Verdict.ERROR);
        assertThat(meta(new Reply(200, Map.of(), body), List.of(), List.of())).isEqualTo(Verdict.ERROR);
    }

    @Test
    void metaserviceNullAndNon2xxWithoutAFaultAreError() {
        assertThat(meta(null, List.of(), List.of())).isEqualTo(Verdict.ERROR);
        assertThat(meta(new Reply(404, Map.of(), "{}"), List.of(), List.of())).isEqualTo(Verdict.ERROR);
        assertThat(meta(new Reply(500, Map.of(), "boom"), List.of(), List.of())).isEqualTo(Verdict.ERROR);
        assertThat(meta(new Reply(302, Map.of(), LIST_JSON), List.of("p-v91"), List.of())).isEqualTo(Verdict.ERROR);
    }

    @Test
    void managementNullAndNon2xxWithoutAnAccessDenialAreError() {
        assertThat(VerdictClassifier.classifyManagement(null)).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classifyManagement(new Reply(500, Map.of(), "boom"))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classifyManagement(new Reply(404, Map.of(), "{}"))).isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classifyManagement(new Reply(302, Map.of(), ""))).isEqualTo(Verdict.ERROR);
    }

    @Test
    void management2xxIsAllowed() {
        assertThat(VerdictClassifier.classifyManagement(new Reply(200, Map.of(), "{}"))).isEqualTo(Verdict.ALLOWED);
        assertThat(VerdictClassifier.classifyManagement(new Reply(201, Map.of(), ""))).isEqualTo(Verdict.ALLOWED);
    }

    @Test
    void managementAccessDeniedFaultCodeInTheBodyIsAccessDenied() {
        assertThat(VerdictClassifier.classifyManagement(new Reply(500, Map.of(),
                "{\"code\":\"core.server.serverproxy.access_denied\"}"))).isEqualTo(Verdict.ACCESS_DENIED);
        assertThat(VerdictClassifier.classifyManagement(new Reply(500, Map.of(), "server.serverproxy.access_denied")))
                .isEqualTo(Verdict.ACCESS_DENIED);
    }

    @Test
    void managementAgreementTokenRejectedFaultCodeInTheBodyIsAgreementTokenRejected() {
        var body = "{\"status\":500,\"error\":{\"code\":\"core.server.serverproxy.agreement_token_rejected\","
                + "\"metadata\":[\"AGREEMENT_TOKEN_REQUIRED\"]}}";
        assertThat(VerdictClassifier.classifyManagement(new Reply(500, Map.of(), body)))
                .isEqualTo(Verdict.AGREEMENT_TOKEN_REJECTED);
        assertThat(VerdictClassifier.classifyManagement(new Reply(500, Map.of(),
                "core.server.serverproxy.agreement_token_rejected"))).isEqualTo(Verdict.AGREEMENT_TOKEN_REJECTED);
    }

    @Test
    void managementOtherAccessDenialWordingIsError() {
        assertThat(VerdictClassifier.classifyManagement(new Reply(403, Map.of(), "ACCESS DENIED for the client")))
                .isEqualTo(Verdict.ERROR);
        assertThat(VerdictClassifier.classifyManagement(new Reply(500, Map.of(), "server.clientproxy.access_denied")))
                .isEqualTo(Verdict.ERROR);
    }

    private static final String HEALTH_REPLY = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:om="http://x-road.eu/xsd/op-monitoring.xsd">
                <soapenv:Body>
                    <om:getSecurityServerHealthDataResponse>
                        <om:monitoringStartupTimestamp>1760000000</om:monitoringStartupTimestamp>
                        <om:statisticsPeriodSeconds>600</om:statisticsPeriodSeconds>
                        %s
                    </om:getSecurityServerHealthDataResponse>
                </soapenv:Body>
            </soapenv:Envelope>
            """;

    private static final String LIST_CLIENTS_REPLY = """
            {"member":[
              {"id":{"xroad_instance":"DEV","member_class":"COM","member_code":"1234","object_type":"MEMBER"},
               "name":"Test member"},
              {"id":{"xroad_instance":"DEV","member_class":"COM","member_code":"4321","object_type":"MEMBER"},
               "name":"Test client"}]}
            """;

    private static final AclScenario.Expectation HEALTH_DATA = new AclScenario.Expectation(
            List.of("getSecurityServerHealthDataResponse", "statisticsPeriodSeconds"), List.of("serviceEvents"),
            List.of(), List.of());

    private static final AclScenario.Expectation OPERATIONAL_DATA =
            AclScenario.Expectation.carrying("getSecurityServerOperationalDataResponse");

    private static final AclScenario.Expectation LIST_CLIENTS =
            AclScenario.Expectation.carrying("1234", "4321", "Test member", "Test client");

    private static Verdict metaCatalogued(Reply reply, AclScenario.Expectation expectation) {
        return meta(reply, expectation.required(List.of()), expectation.forbidden(List.of()));
    }

    @Test
    void healthDataReplyIsAllowedOnlyWithTheResponseElementAndNoServiceEvents() {
        var expectation = HEALTH_DATA;

        assertThat(metaCatalogued(new Reply(200, Map.of(), HEALTH_REPLY.formatted("")), expectation))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(metaCatalogued(new Reply(200, Map.of(),
                HEALTH_REPLY.formatted("<om:servicesEvents/>")), expectation)).isEqualTo(Verdict.ALLOWED);
        assertThat(metaCatalogued(new Reply(200, Map.of(),
                HEALTH_REPLY.formatted("<om:servicesEvents><om:serviceEvents/></om:servicesEvents>")), expectation))
                .isEqualTo(Verdict.ERROR);
        assertThat(metaCatalogued(new Reply(200, Map.of(), "<soapenv:Body/>"), expectation))
                .isEqualTo(Verdict.ERROR);
    }

    @Test
    void operationalDataReplyNeedsTheResponseElementInTheSoapPart() {
        var expectation = OPERATIONAL_DATA;
        var multipart = """
                --jetty123
                Content-Type: text/xml

                <soapenv:Body xmlns:om="http://x-road.eu/xsd/op-monitoring.xsd">
                    <om:getSecurityServerOperationalDataResponse><om:recordsCount>0</om:recordsCount>
                    </om:getSecurityServerOperationalDataResponse>
                </soapenv:Body>
                --jetty123--
                """;

        assertThat(metaCatalogued(new Reply(200, Map.of(), multipart), expectation)).isEqualTo(Verdict.ALLOWED);
        assertThat(metaCatalogued(new Reply(200, Map.of(), "--jetty123--"), expectation)).isEqualTo(Verdict.ERROR);
    }

    @Test
    void monitoringAccessDeniedFaultIsAccessDeniedWhateverTheExpectation() {
        var reply = new Reply(500, Map.of(), soapFault("server.serverproxy.access_denied"));

        assertThat(metaCatalogued(reply, AclScenario.Expectation.NONE)).isEqualTo(Verdict.ACCESS_DENIED);
        assertThat(metaCatalogued(reply, HEALTH_DATA)).isEqualTo(Verdict.ACCESS_DENIED);
    }

    @Test
    void listClientsReplyIsAllowedOnlyWhenItListsTheSeededMembers() {
        var expectation = LIST_CLIENTS;

        assertThat(metaCatalogued(new Reply(200, Map.of(), LIST_CLIENTS_REPLY), expectation))
                .isEqualTo(Verdict.ALLOWED);
        for (var missing : new String[] {"1234", "4321", "Test member", "Test client"}) {
            assertThat(metaCatalogued(new Reply(200, Map.of(), LIST_CLIENTS_REPLY.replace(missing, "other")),
                    expectation)).as(missing).isEqualTo(Verdict.ERROR);
        }
    }

    @Test
    void badRequestTheBackendAnsweredIsNotAConsumerRejection() {
        assertThat(VerdictClassifier.classify(new Reply(400, MARKED, "{}"))).isEqualTo(Verdict.ERROR);
    }

    private static final String DISABLED = "server.serverproxy.service_disabled";
    private static final String NOTICE = "Scheduled maintenance";
    private static Verdict disabledService(Reply reply, String type, String message) {
        return VerdictClassifier.classifyDisabledService(reply, type, message, DISABLED, NOTICE);
    }

    @Test
    void disabledServiceFaultWithTypeAndNoticeIsServiceDisabled() {
        assertThat(disabledService(fault(DISABLED), DISABLED, "Service is disabled: " + NOTICE))
                .isEqualTo(Verdict.SERVICE_DISABLED);
    }

    @Test
    void disabledServiceFaultMissingTypeOrNoticeIsError() {
        assertThat(disabledService(fault(DISABLED), "server.serverproxy.access_denied", NOTICE))
                .isEqualTo(Verdict.ERROR);
        assertThat(disabledService(fault(DISABLED), null, NOTICE)).isEqualTo(Verdict.ERROR);
        assertThat(disabledService(fault(DISABLED), DISABLED, "Service is disabled")).isEqualTo(Verdict.ERROR);
        assertThat(disabledService(fault(DISABLED), DISABLED, null)).isEqualTo(Verdict.ERROR);
    }

    @Test
    void otherReplyToADisabledServiceCallIsError() {
        assertThat(disabledService(fault("server.serverproxy.access_denied"), DISABLED, NOTICE))
                .isEqualTo(Verdict.ERROR);
        assertThat(disabledService(new Reply(200, MARKED, "{}"), DISABLED, NOTICE)).isEqualTo(Verdict.ERROR);
        assertThat(disabledService(null, DISABLED, NOTICE)).isEqualTo(Verdict.ERROR);
    }

    @Test
    void describeWithBodyIncludesTheBody() {
        assertThat(VerdictClassifier.describeWithBody(new Reply(500, Map.of("X-Road-Error", "code"), "the body")))
                .contains("status 500").contains("code").contains("the body");
    }
}
