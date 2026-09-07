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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

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
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_DID_DRIFT;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_IDENTIFIER_MISMATCH;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PULL_DISTRIBUTION_MISSING;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_TRANSFER_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

/**
 * Decides what a consumer may see when a boundary-crossing exception reaches them. A code present in
 * {@link #RULES} is surfaced as the rule's generic code and message, keeping the original code and
 * details in the logs only; an absent code is passed through untouched (opt-in). Only Dataspace
 * Protocol (DSP) codes are flagged today; the class sits beside {@link ErrorCode} so the mechanism can
 * later be reused at other boundaries.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClientFacingErrorPolicy {

    private static final String DATASPACE_SEGMENT = "dataspace.";

    // A DSP-origin code with no RULES entry (e.g. a DSP code added after this policy) would otherwise
    // leak its raw code and details; this narrows it to a safe generic. Non-DSP codes are never touched.
    private static final ClientFacingRule FALLBACK_RULE =
            new ClientFacingRule(IO_ERROR, "The dataspace request could not be completed.");

    private static final Map<ErrorCode, ClientFacingRule> RULES = new EnumMap<>(ErrorCode.class);

    static {
        rule(DSP_CATALOG_FETCH_FAILED, IO_ERROR, "Failed to retrieve service metadata from the provider security server.");
        rule(DSP_CATALOG_PARSE_FAILED, IO_ERROR, "Failed to process service metadata received from the provider security server.");
        rule(DSP_ACQUISITION_TIMEOUT, IO_ERROR, "The request to the provider security server timed out.");
        rule(DSP_ACQUISITION_FAILED, IO_ERROR, "Failed to establish a connection to the provider security server.");
        rule(DSP_DATASET_NOT_FOUND, UNKNOWN_MEMBER, "The requested service was not found on the provider security server.");
        rule(DSP_OFFERS_NOT_FOUND, UNKNOWN_MEMBER, "The requested service is not currently offered by the provider security server.");
        rule(DSP_PULL_DISTRIBUTION_MISSING, SERVICE_FAILED, "The provider security server did not return a valid service access method.");
        rule(DSP_DATAADDRESS_INVALID, SERVICE_FAILED, "The provider security server returned an invalid service access address.");
        rule(DSP_NEGOTIATION_FAILED, SERVICE_FAILED, "Failed to negotiate access terms with the provider security server.");
        rule(DSP_TRANSFER_FAILED, SERVICE_FAILED, "Failed to obtain service access authorization from the provider security server.");
        rule(DSP_PARTICIPANT_CONTEXT_FAILED, INTERNAL_ERROR, "Failed to resolve the participant context for the requested service.");
        rule(DSP_PROVISIONING_FAILED, INTERNAL_ERROR, "Failed to provision access for the requested service.");
        rule(DSP_PARTICIPANT_IDENTIFIER_MISMATCH, INTERNAL_ERROR,
                "The participant identity configured on the security server is inconsistent.");
        rule(DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED, INTERNAL_ERROR,
                "The participant identity configured on the security server uses an unsupported identifier scheme version.");
        rule(DSP_PARTICIPANT_DID_DRIFT, INTERNAL_ERROR,
                "The participant identity published for the security server no longer matches its configuration.");
    }

    private static void rule(ErrorCode dspCode, ErrorCode clientFacingCode, String genericMessage) {
        RULES.put(dspCode, new ClientFacingRule(clientFacingCode, genericMessage));
    }

    static Optional<ClientFacingRule> resolve(ErrorCode code) {
        return Optional.ofNullable(RULES.get(code));
    }

    /**
     * Rebuilds a DSP-origin exception so the consumer sees a generic code and message instead of the
     * original DSP code and details (the original code is kept as {@code originalCode=} log metadata); a
     * non-DSP-origin exception is returned unchanged. The result deliberately leaves {@link ErrorOrigin}
     * unset, so the downstream client-proxy handlers still prepend {@code server.clientproxy.} to the code.
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
