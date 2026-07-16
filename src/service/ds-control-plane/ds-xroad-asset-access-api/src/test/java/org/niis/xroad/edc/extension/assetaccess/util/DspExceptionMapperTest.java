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
package org.niis.xroad.edc.extension.assetaccess.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.stream.Stream;

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
import static org.niis.xroad.common.core.exception.ErrorOrigin.DATASPACE;

class DspExceptionMapperTest {

    static Stream<Arguments> dspToCommonMappings() {
        return Stream.of(
                Arguments.of(DSP_CATALOG_FETCH_FAILED, IO_ERROR),
                Arguments.of(DSP_CATALOG_PARSE_FAILED, IO_ERROR),
                Arguments.of(DSP_ACQUISITION_TIMEOUT, IO_ERROR),
                Arguments.of(DSP_ACQUISITION_FAILED, IO_ERROR),
                Arguments.of(DSP_DATASET_NOT_FOUND, UNKNOWN_MEMBER),
                Arguments.of(DSP_OFFERS_NOT_FOUND, UNKNOWN_MEMBER),
                Arguments.of(DSP_PULL_DISTRIBUTION_MISSING, SERVICE_FAILED),
                Arguments.of(DSP_DATAADDRESS_INVALID, SERVICE_FAILED),
                Arguments.of(DSP_NEGOTIATION_FAILED, SERVICE_FAILED),
                Arguments.of(DSP_TRANSFER_FAILED, SERVICE_FAILED),
                Arguments.of(DSP_PARTICIPANT_CONTEXT_FAILED, INTERNAL_ERROR)
        );
    }

    @ParameterizedTest
    @MethodSource("dspToCommonMappings")
    void dspCodeMapsToExpectedCommonCode(ErrorCode dspCode, ErrorCode expectedCode) {
        var ex = dspException(dspCode);

        var result = DspExceptionMapper.toCommon(ex);

        assertThat(result.isCausedBy(expectedCode)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("dspToCommonMappings")
    void dspCodeOriginalCodePrependedToMetadata(ErrorCode dspCode) {
        var ex = dspException(dspCode, "existing-meta");

        var result = DspExceptionMapper.toCommon(ex);

        assertThat(result.getErrorCodeMetadata().getFirst())
                .isEqualTo("originalCode=" + dspCode.code());
        assertThat(result.getErrorCodeMetadata()).containsOnlyOnce("existing-meta");
    }

    @ParameterizedTest
    @MethodSource("dspToCommonMappings")
    void dspCodeOriginalMetadataOrderPreserved(ErrorCode dspCode) {
        var ex = dspException(dspCode, "meta-a", "meta-b");

        var result = DspExceptionMapper.toCommon(ex);

        assertThat(result.getErrorCodeMetadata())
                .containsExactly("originalCode=" + dspCode.code(), "meta-a", "meta-b");
    }

    @ParameterizedTest
    @MethodSource("dspToCommonMappings")
    void dspCodeIdentifierAndDetailsSurviveMapping(ErrorCode dspCode) {
        var ex = buildDspException(dspCode, "some detail", "fixed-uuid");

        var result = DspExceptionMapper.toCommon(ex);

        assertThat(result.getIdentifier()).isEqualTo("fixed-uuid");
        assertThat(result.getDetails()).isEqualTo("some detail");
    }

    @Test
    void nonDspExceptionPassesThroughUnchanged() {
        var ex = XrdRuntimeException.systemException(NETWORK_ERROR, "plain network error");

        var result = DspExceptionMapper.toCommon(ex);

        assertThat(result).isSameAs(ex);
    }

    @Test
    void unknownDspCodePassesThroughUnchanged() {
        var ex = XrdRuntimeException.systemException(ErrorCode.withCode("dataspace.dsp_unknown_future_code"))
                .build();

        var result = DspExceptionMapper.toCommon(ex);

        assertThat(result).isSameAs(ex);
    }

    @Test
    void errorCodeWithNoDataspaceSegmentPassesThroughUnchanged() {
        var ex = XrdRuntimeException.systemException(NETWORK_ERROR, "no dataspace segment at all");

        var result = DspExceptionMapper.toCommon(ex);

        assertThat(result).isSameAs(ex);
    }

    @Test
    void emptyOriginalMetadataResultsInOnlyOriginalCodeEntry() {
        var ex = dspException(DSP_CATALOG_FETCH_FAILED);

        var result = DspExceptionMapper.toCommon(ex);

        assertThat(result.getErrorCodeMetadata()).hasSize(1);
        assertThat(result.getErrorCodeMetadata().getFirst())
                .isEqualTo("originalCode=" + DSP_CATALOG_FETCH_FAILED.code());
    }

    @Test
    void multiplePrefixesStrippedCorrectly() {
        var ex = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + DSP_CATALOG_FETCH_FAILED.code()))
                .build();

        var result = DspExceptionMapper.toCommon(ex);

        assertThat(result.isCausedBy(IO_ERROR)).isTrue();
    }

    private static XrdRuntimeException dspException(ErrorCode dspCode, String... metadata) {
        return buildDspException(dspCode, null, null, metadata);
    }

    private static XrdRuntimeException buildDspException(ErrorCode dspCode, String details, String identifier,
                                                         String... metadata) {
        var builder = XrdRuntimeException.systemException(dspCode)
                .origin(DATASPACE)
                .details(details)
                .identifier(identifier);
        if (metadata.length > 0) {
            builder.metadataItems((Object[]) metadata);
        }
        return builder.build();
    }
}
