/*
 * The MIT License
 *
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
package org.niis.xroad.edc.extension.assetaccess.dsperror;

import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;

/**
 * Maps {@link EdcPersistenceException} thrown from the JTI replay-protection store to a clean
 * 401 Unauthorized response on the DSP protocol API.
 *
 * <p>EDC's {@code JtiValidationRule} (see {@code org.eclipse.edc.verifiablecredentials.jwt.rules})
 * inserts each accepted JTI into {@code edc_jti_validation}. A duplicate JTI surfaces as a
 * Postgres unique-constraint violation wrapped in {@link EdcPersistenceException}. Without this
 * mapper Jersey treats it as an uncaught server error and returns 500, which Failsafe-wrapped
 * callers (notably {@code EdcHttpClientImpl} on the consumer side) classify as retryable —
 * leading to a retry loop with the same JWT, which is precisely the case the JTI replay store
 * is designed to reject. Mapping it to 401 lets the consumer's Failsafe predicate
 * ({@code retryWhenStatusNot2xxOr4xx}) treat replay rejection as terminal.
 */
@Provider
public class JtiReplayExceptionMapper implements ExceptionMapper<EdcPersistenceException> {

    static final String JTI_PKEY_VIOLATION_FRAGMENT = "edc_jti_validation_pkey";

    private final Monitor monitor;

    public JtiReplayExceptionMapper(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public Response toResponse(EdcPersistenceException exception) {
        if (isJtiReplay(exception)) {
            monitor.warning("DSP token validation: jti replay rejected (duplicate constraint on edc_jti_validation)");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"jti_replay\"}")
                    .build();
        }
        monitor.severe("DSP persistence error", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"persistence\"}")
                .build();
    }

    private static boolean isJtiReplay(@Nullable Throwable cause) {
        for (var cur = cause; cur != null; cur = cur.getCause()) {
            var msg = cur.getMessage();
            if (msg != null && msg.contains(JTI_PKEY_VIOLATION_FRAGMENT)) {
                return true;
            }
        }
        return false;
    }
}
