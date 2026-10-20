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

package org.niis.xroad.e2e.parity;

import org.junit.jupiter.api.Test;

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
    void internalErrorFaultIsInternalError() {
        assertThat(VerdictClassifier.classify(fault("server.serverproxy.internal_error")))
                .isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(VerdictClassifier.classify(fault("server.clientproxy.internal_error"))).isEqualTo(Verdict.ERROR);
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
    void describeSoapNamesTheFaultCodeAndNothingElseOfTheBody() {
        var body = soapFault("Server.ServerProxy.inconsistent_headers") + "<faultstring>secret-detail</faultstring>";

        assertThat(VerdictClassifier.describeSoap(new Reply(500, Map.of(), body)))
                .isEqualTo("fault Server.ServerProxy.inconsistent_headers");
    }

    @Test
    void describeSoapFallsBackToTheStatusWithoutAFaultCode() {
        assertThat(VerdictClassifier.describeSoap(new Reply(404, Map.of(), "<html>not found</html>")))
                .isEqualTo("status 404");
    }
}
