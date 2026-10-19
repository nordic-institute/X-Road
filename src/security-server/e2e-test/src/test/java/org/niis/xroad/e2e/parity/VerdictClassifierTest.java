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
}
