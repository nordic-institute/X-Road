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
package org.niis.xroad.common.core.exception;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_TIMEOUT;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_CATALOG_FETCH_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_CATALOG_PARSE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATAADDRESS_INVALID;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATASET_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_NEGOTIATION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_OFFERS_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PULL_DISTRIBUTION_MISSING;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_TRANSFER_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

/**
 * Data-driven engine deciding what a consumer is allowed to see when a boundary-crossing exception is
 * about to reach them. A code with an entry in {@link #RULES} is surfaced to the consumer as the
 * entry's {@link ClientFacingRule#clientFacingCode()} with a fixed, generic
 * {@link ClientFacingRule#genericMessage()} — the original code and details never reach the wire, only
 * the logs. A code absent from {@link #RULES} is untouched: this is opt-in, so every non-flagged code
 * keeps behaving exactly as before this engine existed.
 *
 * <p>Only Dataspace Protocol (DSP) codes are flagged today; the class lives beside {@link ErrorCode}
 * (not in a DSP-specific module) so the mechanism can later be reused at other boundaries.
 */
public final class ClientFacingErrorPolicy {

    private static final String DATASPACE_SEGMENT = "dataspace.";

    /**
     * Applied when a code is identified as DSP-origin (its wire code carries a {@code dataspace.}
     * segment) but has no entry in {@link #RULES} — e.g. a future DSP code introduced after this
     * policy was last updated. Without this fallback such a code would fall through unsanitized and
     * leak its raw {@code dsp_*} code and internal details to the consumer, defeating the point of the
     * policy. Anything that is *not* DSP-origin (no {@code dataspace.} segment at all) is left alone —
     * this fallback only ever narrows what a DSP-flagged path can expose, it never touches legacy
     * (non-DSP) codes.
     */
    private static final ClientFacingRule FALLBACK_RULE =
            new ClientFacingRule(IO_ERROR, "The dataspace request could not be completed.");

    private static final Map<ErrorCode, ClientFacingRule> RULES = new EnumMap<>(ErrorCode.class);

    static {
        RULES.put(DSP_CATALOG_FETCH_FAILED, new ClientFacingRule(IO_ERROR,
                "Failed to retrieve service metadata from the provider security server."));
        RULES.put(DSP_CATALOG_PARSE_FAILED, new ClientFacingRule(IO_ERROR,
                "Failed to process service metadata received from the provider security server."));
        RULES.put(DSP_ACQUISITION_TIMEOUT, new ClientFacingRule(IO_ERROR,
                "The request to the provider security server timed out."));
        RULES.put(DSP_ACQUISITION_FAILED, new ClientFacingRule(IO_ERROR,
                "Failed to establish a connection to the provider security server."));
        RULES.put(DSP_DATASET_NOT_FOUND, new ClientFacingRule(UNKNOWN_MEMBER,
                "The requested service was not found on the provider security server."));
        RULES.put(DSP_OFFERS_NOT_FOUND, new ClientFacingRule(UNKNOWN_MEMBER,
                "The requested service is not currently offered by the provider security server."));
        RULES.put(DSP_PULL_DISTRIBUTION_MISSING, new ClientFacingRule(SERVICE_FAILED,
                "The provider security server did not return a valid access method for the requested service."));
        RULES.put(DSP_DATAADDRESS_INVALID, new ClientFacingRule(SERVICE_FAILED,
                "The provider security server returned an invalid service access address."));
        RULES.put(DSP_NEGOTIATION_FAILED, new ClientFacingRule(SERVICE_FAILED,
                "Failed to negotiate access terms with the provider security server."));
        RULES.put(DSP_TRANSFER_FAILED, new ClientFacingRule(SERVICE_FAILED,
                "Failed to obtain service access authorization from the provider security server."));
        RULES.put(DSP_PARTICIPANT_CONTEXT_FAILED, new ClientFacingRule(INTERNAL_ERROR,
                "Failed to resolve the participant context for the requested service."));
        RULES.put(DSP_PROVISIONING_FAILED, new ClientFacingRule(INTERNAL_ERROR,
                "Failed to provision access for the requested service."));
    }

    private ClientFacingErrorPolicy() {
    }

    /**
     * Looks up the rule for a code, if any.
     *
     * @param code the resolved DSP error code
     * @return the rule, or empty if the code is not flagged
     */
    static Optional<ClientFacingRule> resolve(ErrorCode code) {
        return Optional.ofNullable(RULES.get(code));
    }

    /**
     * Rebuilds {@code ex} for consumer consumption: if {@code ex}'s code identifies it as DSP-origin,
     * the returned exception carries the {@link ClientFacingRule#clientFacingCode()} and
     * {@link ClientFacingRule#genericMessage()} in place of the original code and details (falling back
     * to {@link #FALLBACK_RULE} for a DSP-origin code with no rule), and the original DSP code is kept
     * as {@code originalCode=} log-only metadata. A non-DSP-origin {@code ex} is returned unchanged.
     *
     * <p>The result never has {@link ErrorOrigin} set, matching the mechanism this replaces: leaving
     * origin unset keeps the code bare so the downstream client-proxy handlers still prepend
     * {@code server.clientproxy.} to it.
     *
     * @param ex the exception about to cross the consumer-facing boundary
     * @return the sanitized exception, or {@code ex} itself if it is not DSP-origin
     */
    public static XrdRuntimeException sanitizeForConsumer(XrdRuntimeException ex) {
        var dspCode = extractDspCode(ex.getErrorCode());
        if (dspCode == null) {
            return ex;
        }

        var dspErrorCode = ErrorCode.fromCode(dspCode);
        var rule = dspErrorCode == null ? FALLBACK_RULE : resolve(dspErrorCode).orElse(FALLBACK_RULE);

        var metadata = Stream.concat(
                Stream.of("originalCode=" + dspCode),
                ex.getErrorCodeMetadata().stream()).toArray();
        return new XrdRuntimeExceptionBuilder<>(rule.clientFacingCode())
                .identifier(ex.getIdentifier())
                .cause(ex.getCause())
                .details(rule.genericMessage())
                .metadataItems(metadata)
                .build();
    }

    private static String extractDspCode(String errorCode) {
        int idx = errorCode.lastIndexOf(DATASPACE_SEGMENT);
        return idx < 0 ? null : errorCode.substring(idx + DATASPACE_SEGMENT.length());
    }
}
