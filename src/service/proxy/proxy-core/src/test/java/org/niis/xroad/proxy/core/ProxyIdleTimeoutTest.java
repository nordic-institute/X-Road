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
import org.junit.jupiter.api.Test;
import org.niis.xroad.proxy.core.test.TestService;

import java.io.IOException;
import java.net.Socket;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request that waits for the service longer than the idle timeout of the client proxy and server proxy connectors
 * still gets its response.
 */
class ProxyIdleTimeoutTest extends AbstractProxyIntegrationTest {

    private static final String PREFIX = "/r" + RestMessage.PROTOCOL_VERSION;
    private static final int IDLE_TIMEOUT_MILLIS = 1000;
    private static final int SERVICE_DELAY_MILLIS = 2500;

    @BeforeAll
    static void shortenIdleTimeouts() throws Exception {
        ADDITIONAL_PROPERTIES.put("xroad.proxy.client-proxy.client-connector-initial-idle-time", String.valueOf(IDLE_TIMEOUT_MILLIS));
        ADDITIONAL_PROPERTIES.put("xroad.proxy.server.connector-initial-idle-time", String.valueOf(IDLE_TIMEOUT_MILLIS));
        restartProxies();
    }

    @Test
    void idleConnectionsOfTheConnectorsAreClosedAfterTheIdleTimeout() throws IOException {
        assertThat(isClosedByTheServerWhileIdle(proxyClientPort)).isTrue();
        assertThat(isClosedByTheServerWhileIdle(proxyServerPort)).isTrue();
    }

    @Test
    void requestWaitingForTheServiceLongerThanTheIdleTimeoutGetsItsResponse() {
        setServiceHandler((request, response) -> {
            Thread.sleep(SERVICE_DELAY_MILLIS);
            TestService.ECHO.handle(request, response);
        });

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .body("{\"value\" : 42}")
                .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .then()
                .statusCode(200)
                .body("value", Matchers.equalTo(42));
    }

    private static boolean isClosedByTheServerWhileIdle(int port) throws IOException {
        try (var socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(SERVICE_DELAY_MILLIS);
            return socket.getInputStream().read() == -1;
        }
    }
}
