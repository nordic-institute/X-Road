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
package org.niis.xroad.e2e;

import io.restassured.response.ValidatableResponse;
import lombok.experimental.UtilityClass;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

/**
 * ss0's own {@code TestService} subsystem and its self-accessible {@code mock1} REST service: the fixture
 * identifiers every scenario calling it shares, and the calls through the client-side proxy that exercise it.
 */
@UtilityClass
class Mock1Fixture {

    static final String MOCK1_CLIENT_ID = "DEV:COM:1234:TestService";
    static final String MOCK1_SERVICE_CODE = "mock1";
    static final String MOCK1_SERVICE_ID = MOCK1_CLIENT_ID + ":" + MOCK1_SERVICE_CODE;
    static final String MOCK1_X_ROAD_CLIENT = "DEV/COM/1234/TestService";
    static final String MOCK1_SERVICE_PATH = "/r1/DEV/COM/1234/TestService/mock1";
    static final String MOCK1_REQUEST_BODY = """
            {"data": 1.0, "service": "random"}
            """;

    /** Sends a REST POST as {@code xRoadClient} to {@code servicePath} on {@code envName}'s mapped proxy. */
    static ValidatableResponse callService(E2eEnvironment env, String envName, String xRoadClient, String servicePath) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        return RestAssuredFactory.given()
                .body(MOCK1_REQUEST_BODY)
                .header("Content-Type", "application/json")
                .header("x-road-client", xRoadClient)
                .post("http://%s:%s%s".formatted(mapping.host(), mapping.port(), servicePath))
                .then();
    }

    /** Sends the {@code mock1} REST POST as {@link #MOCK1_X_ROAD_CLIENT} to {@code envName}'s mapped proxy. */
    static ValidatableResponse callMock1(E2eEnvironment env, String envName) {
        return callService(env, envName, MOCK1_X_ROAD_CLIENT, MOCK1_SERVICE_PATH);
    }
}
