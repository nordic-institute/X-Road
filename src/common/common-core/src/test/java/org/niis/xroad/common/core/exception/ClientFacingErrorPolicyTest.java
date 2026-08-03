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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
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
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PULL_DISTRIBUTION_MISSING;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_TRANSFER_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

class ClientFacingErrorPolicyTest {

    static Stream<Arguments> dspToClientFacingMappings() {
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
                Arguments.of(DSP_PARTICIPANT_CONTEXT_FAILED, INTERNAL_ERROR),
                Arguments.of(DSP_PROVISIONING_FAILED, INTERNAL_ERROR)
        );
    }

    @ParameterizedTest
    @MethodSource("dspToClientFacingMappings")
    void dspCodeMapsToExpectedClientFacingCode(ErrorCode dspCode, ErrorCode expectedClientFacingCode) {
        var ex = dspExceptionWithDoublePrefix(dspCode, "sensitive internal detail");

        var result = ClientFacingErrorPolicy.sanitizeForConsumer(ex);

        assertThat(result.isCausedBy(expectedClientFacingCode)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("dspToClientFacingMappings")
    void dspCodeDetailsReplacedWithGenericMessageNoLeak(ErrorCode dspCode, ErrorCode ignoredClientFacingCode) {
        var ex = dspExceptionWithDoublePrefix(dspCode, "host=xrd-ss1.lxd candidates=3 dataset=urn:asset:1234");

        var result = ClientFacingErrorPolicy.sanitizeForConsumer(ex);

        assertThat(result.getDetails())
                .isNotBlank()
                .doesNotContain("xrd-ss1.lxd")
                .doesNotContain("candidates=3")
                .doesNotContainIgnoringCase("dsp_")
                .doesNotContainIgnoringCase("edc");
    }

    @ParameterizedTest
    @MethodSource("dspToClientFacingMappings")
    void dspCodeOriginalCodePrependedToMetadata(ErrorCode dspCode, ErrorCode ignoredClientFacingCode) {
        var ex = dspExceptionWithDoublePrefix(dspCode, null, "existing-meta");

        var result = ClientFacingErrorPolicy.sanitizeForConsumer(ex);

        assertThat(result.getErrorCodeMetadata().getFirst())
                .isEqualTo("originalCode=" + dspCode.code());
        assertThat(result.getErrorCodeMetadata()).containsOnlyOnce("existing-meta");
    }

    @ParameterizedTest
    @MethodSource("dspToClientFacingMappings")
    void dspCodeIdentifierAndCauseSurviveSanitizing(ErrorCode dspCode, ErrorCode ignoredClientFacingCode) {
        var cause = new RuntimeException("root cause");
        var ex = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy." + ErrorOrigin.DATASPACE.toPrefix() + dspCode.code()))
                .identifier("fixed-uuid")
                .cause(cause)
                .details("some detail")
                .build();

        var result = ClientFacingErrorPolicy.sanitizeForConsumer(ex);

        assertThat(result.getIdentifier()).isEqualTo("fixed-uuid");
        assertThat(result.getCause()).isSameAs(cause);
    }

    @Test
    void resultHasNoOriginSetSoDownstreamPrefixSeamStillApplies() {
        var ex = dspExceptionWithDoublePrefix(DSP_CATALOG_FETCH_FAILED, "detail");

        var result = ClientFacingErrorPolicy.sanitizeForConsumer(ex);

        assertThat(result.originatesFrom(ErrorOrigin.CLIENT)).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(IO_ERROR.code());
    }

    @Test
    void nonDspExceptionPassesThroughUnchanged() {
        var ex = XrdRuntimeException.systemException(NETWORK_ERROR, "plain network error");

        var result = ClientFacingErrorPolicy.sanitizeForConsumer(ex);

        assertThat(result).isSameAs(ex);
        assertThat(result.getDetails()).isEqualTo("plain network error");
    }

    @Test
    void errorCodeWithNoDataspaceSegmentPassesThroughUnchanged() {
        var ex = XrdRuntimeException.systemException(NETWORK_ERROR, "no dataspace segment at all");

        var result = ClientFacingErrorPolicy.sanitizeForConsumer(ex);

        assertThat(result).isSameAs(ex);
    }

    @Test
    void unknownFutureDspCodeIsCollapsedToFallbackNotPassedThrough() {
        var ex = XrdRuntimeException.systemException(ErrorCode.withCode("dataspace.dsp_unknown_future_code"))
                .details("host=xrd-ss9.lxd unreachable via edc catalog API")
                .build();

        var result = ClientFacingErrorPolicy.sanitizeForConsumer(ex);

        assertThat(result).isNotSameAs(ex);
        assertThat(result.getErrorCode()).isEqualTo(IO_ERROR.code());
        assertThat(result.getDetails())
                .isNotBlank()
                .doesNotContain("xrd-ss9.lxd")
                .doesNotContainIgnoringCase("dsp_")
                .doesNotContainIgnoringCase("edc");
        assertThat(result.getErrorCodeMetadata()).containsExactly("originalCode=dsp_unknown_future_code");
    }

    @Test
    void multiplePrefixesStrippedCorrectly() {
        var ex = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + DSP_CATALOG_FETCH_FAILED.code()))
                .build();

        var result = ClientFacingErrorPolicy.sanitizeForConsumer(ex);

        assertThat(result.isCausedBy(IO_ERROR)).isTrue();
    }

    @Test
    void everyDspErrorCodeHasAClientFacingRule() {
        var dspCodes = Arrays.stream(ErrorCode.values())
                .filter(code -> code.name().startsWith("DSP_"))
                .toList();

        assertThat(dspCodes).isNotEmpty();
        dspCodes.forEach(dspCode -> assertThat(ClientFacingErrorPolicy.resolve(dspCode))
                .as("no ClientFacingRule registered for %s — every DSP_* code must be classified", dspCode)
                .isPresent());
    }

    private static XrdRuntimeException dspExceptionWithDoublePrefix(ErrorCode dspCode, String details, String... metadata) {
        var builder = XrdRuntimeException.systemException(
                ErrorCode.withCode("proxy." + ErrorOrigin.DATASPACE.toPrefix() + dspCode.code()));
        if (details != null) {
            builder.details(details);
        }
        if (metadata.length > 0) {
            builder.metadataItems((Object[]) metadata);
        }
        return builder.build();
    }
}
