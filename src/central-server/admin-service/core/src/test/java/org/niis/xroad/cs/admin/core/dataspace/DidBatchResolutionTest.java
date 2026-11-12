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
package org.niis.xroad.cs.admin.core.dataspace;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.niis.xroad.cs.admin.core.dataspace.DidBatchResolution.Result;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.cs.admin.core.dataspace.DidBatchResolution.Status.NOT_RESOLVED;
import static org.niis.xroad.cs.admin.core.dataspace.DidBatchResolution.Status.RESOLVED;
import static org.niis.xroad.cs.admin.core.dataspace.DidBatchResolution.Status.RESOLVER_UNAVAILABLE;

class DidBatchResolutionTest {

    private static final String DID_1 = "did:web:ss0.example.org%3A7183:v1:DEV:COM:1";
    private static final String DID_2 = "did:web:ss0.example.org%3A7183:v1:DEV:COM:2";
    private static final String DID_3 = "did:web:ss0.example.org%3A7183:v1:DEV:COM:3";
    private static final String OTHER_DID = "did:web:other.example.org%3A7183:v1:system";

    @Test
    void shouldReportEveryDidUnavailableWhenTheResolverFailsAtTransportLevel() {
        DidResolutionService resolver = dids -> {
            throw XrdRuntimeException.systemException(NETWORK_ERROR).details("UNAVAILABLE").build();
        };

        assertThat(new DidBatchResolution(resolver).resolve(List.of(DID_1, DID_2))).extracting(Result::status)
                .containsExactly(RESOLVER_UNAVAILABLE, RESOLVER_UNAVAILABLE);
    }

    @Test
    void shouldReportEveryDidUnavailableWhenTheResolverFailsWithAnyOtherError() {
        DidResolutionService resolver = dids -> {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR).details("unknown outcome").build();
        };

        assertThat(new DidBatchResolution(resolver).resolve(List.of(DID_1, DID_2))).extracting(Result::status)
                .containsExactly(RESOLVER_UNAVAILABLE, RESOLVER_UNAVAILABLE);
    }

    @Test
    void shouldReportUnavailableWhenTheResolverAnswersEmpty() {
        assertThat(new DidBatchResolution(answering(List.of())).resolve(List.of(DID_1))).extracting(Result::status)
                .containsExactly(RESOLVER_UNAVAILABLE);
    }

    @Test
    void shouldReportUnavailableForTheDidsTheAnswerDoesNotReach() {
        var resolver = answering(List.of(DidResolution.resolved(DID_1)));

        assertThat(new DidBatchResolution(resolver).resolve(List.of(DID_1, DID_2))).extracting(Result::status)
                .containsExactly(RESOLVED, RESOLVER_UNAVAILABLE);
    }

    @Test
    void shouldReportUnavailableWhenTheAnswerIsForAnotherDid() {
        var resolver = answering(List.of(DidResolution.resolved(OTHER_DID)));

        var results = new DidBatchResolution(resolver).resolve(List.of(DID_1));

        assertThat(results).extracting(Result::status).containsExactly(RESOLVER_UNAVAILABLE);
        assertThat(results.getFirst().resolution()).isNull();
    }

    @Test
    void shouldReportNotResolvedWithTheResolversAnswer() {
        var answer = DidResolution.notResolved(DID_1, DidResolutionReason.NO_DOCUMENT);

        var results = new DidBatchResolution(answering(List.of(answer))).resolve(List.of(DID_1));

        assertThat(results).containsExactly(new Result(NOT_RESOLVED, answer));
    }

    @Test
    void shouldReportResolved() {
        var answer = DidResolution.resolved(DID_1);

        var results = new DidBatchResolution(answering(List.of(answer))).resolve(List.of(DID_1));

        assertThat(results).containsExactly(new Result(RESOLVED, answer));
    }

    @Test
    void shouldKeepTheOrderOfAMixedBatch() {
        var notResolved = DidResolution.notResolved(DID_2, DidResolutionReason.NO_DOCUMENT);
        var resolver = answering(List.of(
                DidResolution.resolved(DID_1), notResolved, DidResolution.resolved(OTHER_DID)));

        var results = new DidBatchResolution(resolver).resolve(List.of(DID_1, DID_2, DID_3));

        assertThat(results).extracting(Result::status).containsExactly(RESOLVED, NOT_RESOLVED, RESOLVER_UNAVAILABLE);
        assertThat(results.get(1).resolution()).isEqualTo(notResolved);
    }

    private static DidResolutionService answering(List<DidResolution> answer) {
        return dids -> answer;
    }
}
