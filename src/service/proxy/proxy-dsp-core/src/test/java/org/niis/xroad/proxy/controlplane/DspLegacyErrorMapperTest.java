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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.stream.Stream;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static org.assertj.core.api.Assertions.assertThat;
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
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

class DspLegacyErrorMapperTest {

    static Stream<Arguments> dspToLegacyMappings() {
        return Stream.of(
                Arguments.of(DSP_CATALOG_FETCH_FAILED, SERVER_SERVERPROXY_X, IO_ERROR),
                Arguments.of(DSP_CATALOG_PARSE_FAILED, SERVER_SERVERPROXY_X, IO_ERROR),
                Arguments.of(DSP_ACQUISITION_TIMEOUT, SERVER_SERVERPROXY_X, IO_ERROR),
                Arguments.of(DSP_ACQUISITION_FAILED, SERVER_SERVERPROXY_X, IO_ERROR),
                Arguments.of(DSP_DATASET_NOT_FOUND, SERVER_CLIENTPROXY_X, UNKNOWN_MEMBER),
                Arguments.of(DSP_OFFERS_NOT_FOUND, SERVER_CLIENTPROXY_X, UNKNOWN_MEMBER),
                Arguments.of(DSP_PULL_DISTRIBUTION_MISSING, SERVER_CLIENTPROXY_X, SERVICE_FAILED),
                Arguments.of(DSP_DATAADDRESS_INVALID, SERVER_CLIENTPROXY_X, SERVICE_FAILED),
                Arguments.of(DSP_NEGOTIATION_FAILED, SERVER_SERVERPROXY_X, SERVICE_FAILED),
                Arguments.of(DSP_TRANSFER_FAILED, SERVER_SERVERPROXY_X, SERVICE_FAILED),
                Arguments.of(DSP_PARTICIPANT_CONTEXT_FAILED, SERVER_SERVERPROXY_X, INTERNAL_ERROR)
        );
    }

    @ParameterizedTest
    @MethodSource("dspToLegacyMappings")
    void dspCodeMapsToExpectedLegacyCode(ErrorCode dspCode, String expectedPrefix, ErrorCode expectedLegacy) {
        var ex = dspExceptionWithDoublePrefix(dspCode);

        var result = DspLegacyErrorMapper.toLegacy(ex);

        assertThat(result.isCausedBy(expectedLegacy)).isTrue();
        assertThat(result.getCode()).isEqualTo(expectedPrefix + "." + expectedLegacy.code());
    }

    @ParameterizedTest
    @MethodSource("dspToLegacyMappings")
    void dspCodeOriginalCodePrependedToMetadata(ErrorCode dspCode) {
        var ex = dspExceptionWithDoublePrefix(dspCode, "existing-meta");

        var result = DspLegacyErrorMapper.toLegacy(ex);

        assertThat(result.getErrorCodeMetadata().getFirst())
                .isEqualTo("originalCode=" + dspCode.code());
        assertThat(result.getErrorCodeMetadata()).containsOnlyOnce("existing-meta");
    }

    @ParameterizedTest
    @MethodSource("dspToLegacyMappings")
    void dspCodeOriginalMetadataOrderPreserved(ErrorCode dspCode) {
        var ex = dspExceptionWithDoublePrefix(dspCode, "meta-a", "meta-b");

        var result = DspLegacyErrorMapper.toLegacy(ex);

        assertThat(result.getErrorCodeMetadata())
                .containsExactly("originalCode=" + dspCode.code(), "meta-a", "meta-b");
    }

    @ParameterizedTest
    @MethodSource("dspToLegacyMappings")
    void dspCodeIdentifierAndDetailsSurviveMapping(ErrorCode dspCode) {
        var doublePrefix = buildDoublePrefixException(dspCode, "some detail", "fixed-uuid");

        var result = DspLegacyErrorMapper.toLegacy(doublePrefix);

        assertThat(result.getIdentifier()).isEqualTo("fixed-uuid");
        assertThat(result.getDetails()).isEqualTo("some detail");
    }

    @Test
    void nonDspExceptionPassesThroughUnchanged() {
        var ex = XrdRuntimeException.systemException(NETWORK_ERROR, "plain network error");

        var result = DspLegacyErrorMapper.toLegacy(ex);

        assertThat(result).isSameAs(ex);
    }

    @Test
    void unknownDspCodePassesThroughUnchanged() {
        var ex = XrdRuntimeException.systemException(ErrorCode.withCode("dataspace.dsp_unknown_future_code"))
                .build();

        var result = DspLegacyErrorMapper.toLegacy(ex);

        assertThat(result).isSameAs(ex);
    }

    @Test
    void errorCodeWithNoDataspaceSegmentPassesThroughUnchanged() {
        var ex = XrdRuntimeException.systemException(NETWORK_ERROR, "no dataspace segment at all");

        var result = DspLegacyErrorMapper.toLegacy(ex);

        assertThat(result).isSameAs(ex);
    }

    @Test
    void emptyOriginalMetadataResultsInOnlyOriginalCodeEntry() {
        var ex = dspExceptionWithDoublePrefix(DSP_CATALOG_FETCH_FAILED);

        var result = DspLegacyErrorMapper.toLegacy(ex);

        assertThat(result.getErrorCodeMetadata()).hasSize(1);
        assertThat(result.getErrorCodeMetadata().getFirst())
                .isEqualTo("originalCode=" + DSP_CATALOG_FETCH_FAILED.code());
    }

    @Test
    void multiplePrefixesStrippedCorrectly() {
        var ex = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + DSP_CATALOG_FETCH_FAILED.code()))
                .build();

        var result = DspLegacyErrorMapper.toLegacy(ex);

        assertThat(result.isCausedBy(IO_ERROR)).isTrue();
    }

    private static XrdRuntimeException dspExceptionWithDoublePrefix(ErrorCode dspCode, String... metadata) {
        return buildDoublePrefixException(dspCode, null, null, metadata);
    }

    private static XrdRuntimeException buildDoublePrefixException(ErrorCode dspCode, String details, String identifier,
                                                                   String... metadata) {
        var builder = XrdRuntimeException.systemException(
                ErrorCode.withCode("proxy." + ErrorOrigin.DATASPACE.toPrefix() + dspCode.code()));
        if (details != null) {
            builder.details(details);
        }
        if (identifier != null) {
            builder.identifier(identifier);
        }
        if (metadata.length > 0) {
            builder.metadataItems((Object[]) metadata);
        }
        return builder.build();
    }
}
