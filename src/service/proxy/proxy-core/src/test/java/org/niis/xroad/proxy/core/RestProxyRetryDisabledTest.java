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
package org.niis.xroad.proxy.core;

import ee.ria.xroad.common.message.RestMessage;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.proxy.core.RestRetryTestUtil.TestAddressResolver;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.proxy.core.RestRetryTestUtil.startRejectingProxy;
import static org.niis.xroad.proxy.core.RestRetryTestUtil.uri;

/**
 * Verifies that with request retry disabled a mid-request TLS handshake failure is not
 * retried: the request fails after a single attempt, as it did before the retry feature.
 */
class RestProxyRetryDisabledTest extends AbstractProxyIntegrationTest {

    private static final String PREFIX = "/r" + RestMessage.PROTOCOL_VERSION;

    private static final TestAddressResolver RESOLVER = new TestAddressResolver();

    @BeforeAll
    static void disableRetryAndUseScriptedResolver() throws Exception {
        ADDITIONAL_PROPERTIES.put("xroad.proxy.client-proxy.enable-request-retry", "false");
        serviceAddressResolverOverride = RESOLVER;
        restartProxies();
    }

    @BeforeEach
    void resetResolver() {
        RESOLVER.reset();
    }

    @Test
    void shouldNotRetryWhenRetryIsDisabled() throws Exception {
        int badPort = getFreePort();
        int decoyPort = getFreePort();
        var badProxy = startRejectingProxy(badPort, serverKeyConf.getAuthKey());
        try {
            // a retry would succeed against the real server proxy and turn the response into 200,
            // so a 500 here proves no retry was attempted
            RESOLVER.plan(List.of(
                    List.of(uri(badPort), uri(decoyPort)), // consumed by the op-monitoring address lookup
                    List.of(uri(badPort), uri(decoyPort)),
                    List.of(uri(proxyServerPort))));

            given()
                    .baseUri("http://127.0.0.1")
                    .port(proxyClientPort)
                    .header("Content-Type", "application/json")
                    .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                    .body("{\"value\" : 42}")
                    .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                    .then()
                    .statusCode(500)
                    // depending on whether the rejection alert interrupts the request write or the
                    // response read, the error is network_error or ssl_authentication_failed —
                    // both are the pre-retry-feature behavior
                    .header("X-Road-Error", Matchers.notNullValue());

            // op-monitoring lookup + the single attempt
            assertThat(RESOLVER.resolveCount()).isEqualTo(2);
        } finally {
            badProxy.destroy();
        }
    }
}
