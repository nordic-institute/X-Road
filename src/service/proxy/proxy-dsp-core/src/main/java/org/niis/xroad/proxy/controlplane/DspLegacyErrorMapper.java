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
package org.niis.xroad.proxy.controlplane;

import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.exception.XrdRuntimeExceptionBuilder;

import java.util.Map;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_TIMEOUT;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_CATALOG_FETCH_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_CATALOG_PARSE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATAADDRESS_INVALID;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATASET_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_NEGOTIATION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_OFFERS_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PULL_DISTRIBUTION_MISSING;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_TRANSFER_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

/**
 * Translates DSP-origin {@link XrdRuntimeException}s into legacy XRoad error codes.
 *
 * <p>Needed because {@code AbstractRpcClient} overwrites the structured {@code origin} field and
 * double-prefixes the wire code (e.g. {@code proxy.dataspace.dsp_catalog_fetch_failed}). The mapper
 * identifies DSP exceptions by scanning the {@code errorCode} string for a {@code dataspace.} segment
 * rather than relying on the structured origin enum.
 */
@UtilityClass
class DspLegacyErrorMapper {

    private static final String DATASPACE_SEGMENT = "dataspace.";

    private static final Map<ErrorCode, ErrorCode> DSP_TO_LEGACY = Map.ofEntries(
            entry(DSP_CATALOG_FETCH_FAILED, IO_ERROR),
            entry(DSP_CATALOG_PARSE_FAILED, IO_ERROR),
            entry(DSP_ACQUISITION_TIMEOUT, IO_ERROR),
            entry(DSP_ACQUISITION_FAILED, IO_ERROR),
            entry(DSP_DATASET_NOT_FOUND, UNKNOWN_MEMBER),
            entry(DSP_OFFERS_NOT_FOUND, UNKNOWN_MEMBER),
            entry(DSP_PULL_DISTRIBUTION_MISSING, SERVICE_FAILED),
            entry(DSP_DATAADDRESS_INVALID, SERVICE_FAILED),
            entry(DSP_NEGOTIATION_FAILED, SERVICE_FAILED),
            entry(DSP_TRANSFER_FAILED, SERVICE_FAILED),
            entry(DSP_PARTICIPANT_CONTEXT_FAILED, INTERNAL_ERROR));

    static XrdRuntimeException toLegacy(XrdRuntimeException ex) {
        var dspCode = extractDspCode(ex.getErrorCode());
        if (dspCode == null) {
            return ex;
        }
        var dspErrorCode = ErrorCode.fromCode(dspCode);
        if (dspErrorCode == null) {
            return ex;
        }
        var legacyCode = DSP_TO_LEGACY.get(dspErrorCode);
        if (legacyCode == null) {
            return ex;
        }
        var metadata = Stream.concat(
                Stream.of("originalCode=" + dspCode),
                ex.getErrorCodeMetadata().stream()).toArray();
        return new XrdRuntimeExceptionBuilder<>(legacyCode)
                .identifier(ex.getIdentifier())
                .cause(ex.getCause())
                .details(ex.getDetails())
                .metadataItems(metadata)
                .build();
    }

    private static String extractDspCode(String errorCode) {
        int idx = errorCode.lastIndexOf(DATASPACE_SEGMENT);
        return idx < 0 ? null : errorCode.substring(idx + DATASPACE_SEGMENT.length());
    }
}
