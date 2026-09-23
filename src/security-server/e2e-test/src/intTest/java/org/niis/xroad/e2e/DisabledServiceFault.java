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

import ee.ria.xroad.common.util.MimeUtils;

import io.restassured.response.ValidatableResponse;
import lombok.experimental.UtilityClass;
import org.awaitility.Awaitility;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.properties.config.keys.ServerConfConfigKeys;

import java.time.Duration;
import java.util.function.Supplier;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * The operator's disabled-service notice and the fault it produces on the wire, shared by
 * {@link SsProxyServiceDisableRoundTripTest} and {@link SsProxyFreshNegotiationDisabledNoticeTest}: both
 * disable a service description with the same notice and poll for the same {@code SERVICE_DISABLED} fault
 * shape, differing only in which call they poll and, for the fresh-negotiation scenario, in how long the
 * poll waits for the provider's catalog to pick up a brand new service.
 *
 * <p>The provider proxy only stops refusing calls (and only starts refusing them) once its own
 * {@code CachingServerConfImpl} reload picks up the disabled flag — there is no invalidation signal on
 * either transition, so both {@link #SERVERCONF_CACHE_EXPIRY_TIMEOUT} waits are bounded by that cache's
 * own TTL, {@link ServerConfConfigKeys#CACHE_PERIOD}, plus slack for the poll and the request itself.
 *
 * <p><b>Fault shape.</b> The provider's {@code ServerRestMessageProcessor} raises {@code SERVICE_DISABLED}
 * after the access-rights check, carrying the notice in its details, and prefixes the code with
 * {@code server.serverproxy} as it encodes the fault. The fault crosses the wire as a SOAP fault (X-Road's
 * proxy-to-proxy protocol represents faults this way even for REST calls); on the consumer side a
 * {@code server.}-prefixed code makes the client-facing handler answer 500, not 400. The REST caller
 * therefore sees an HTTP 500 with an {@code X-Road-Error} header and a JSON body whose {@code type} carries
 * the same prefixed code and whose {@code message} carries the exact notice text.
 */
@UtilityClass
@SuppressWarnings("checkstyle:magicnumber")
class DisabledServiceFault {

    static final String SS0_ENV = "ss0";

    static final String EXPECTED_RESPONSE_MESSAGE = "Hello, world from POST service!";
    static final String DISABLED_NOTICE = "Scheduled maintenance window, please retry once it closes";

    static final String EXPECTED_FAULT_CODE = SERVER_SERVERPROXY_X + "." + ErrorCode.SERVICE_DISABLED.code();

    static final Duration SERVERCONF_CACHE_EXPIRY_TIMEOUT =
            Duration.ofSeconds(ServerConfConfigKeys.CACHE_PERIOD.convertedDefaultValue() + 30);
    static final Duration SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL = Duration.ofSeconds(3);

    /** Polls {@code call} until it returns the expected POST service response. */
    static void awaitCallSucceeds(Supplier<ValidatableResponse> call, Duration timeout, Duration pollInterval) {
        Awaitility.await()
                .pollDelay(Duration.ZERO)
                .pollInterval(pollInterval)
                .timeout(timeout)
                .ignoreExceptions()
                .untilAsserted(() -> call.get()
                        .statusCode(200)
                        .body("message", equalTo(EXPECTED_RESPONSE_MESSAGE)));
    }

    /** Polls {@code call} until it returns the {@code SERVICE_DISABLED} fault carrying the operator's notice. */
    static void awaitCallFailsWithDisabledNotice(Supplier<ValidatableResponse> call, Duration timeout, Duration pollInterval) {
        Awaitility.await()
                .pollDelay(Duration.ZERO)
                .pollInterval(pollInterval)
                .timeout(timeout)
                .ignoreExceptions()
                .untilAsserted(() -> call.get()
                        .statusCode(500)
                        .header(MimeUtils.HEADER_ERROR, equalTo(EXPECTED_FAULT_CODE))
                        .body("type", equalTo(EXPECTED_FAULT_CODE))
                        .body("message", containsString(DISABLED_NOTICE)));
    }
}
