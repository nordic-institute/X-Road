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

package org.niis.xroad.edc.protocol.assetaccess;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFailureClassifierTest {

    private static String providerAnswer(int code, String body) {
        return ("Counter Party responded with Response{protocol=h2, code=%d, message=, "
                + "url=https://provider.example/dsp/transfers/request}. Body: %s.").formatted(code, body);
    }

    @Test
    void aConflictIsARefusal() {
        assertThat(ProviderFailureClassifier.isRefusal(providerAnswer(409, "{\"message\":\"anything\"}"))).isTrue();
    }

    @Test
    void aNotFoundNamingTheAgreementIsARefusal() {
        var body = "Cannot process TransferRequestMessage because agreement not found or not valid";

        assertThat(ProviderFailureClassifier.isRefusal(providerAnswer(404, body))).isTrue();
    }

    @Test
    void theAgreementWordingIsMatchedIgnoringCase() {
        assertThat(ProviderFailureClassifier.isRefusal(providerAnswer(404, "Agreement Not Found Or Not Valid"))).isTrue();
    }

    @Test
    void aBareNotFoundIsNotARefusal() {
        assertThat(ProviderFailureClassifier.isRefusal(providerAnswer(404, "Not found"))).isFalse();
    }

    @Test
    void aBadRequestIsNotARefusal() {
        assertThat(ProviderFailureClassifier.isRefusal(providerAnswer(400, "TransferType is not supported"))).isFalse();
    }

    @Test
    void theAgreementWordingUnderAnotherStatusIsNotARefusal() {
        assertThat(ProviderFailureClassifier.isRefusal(providerAnswer(400, "agreement not found or not valid"))).isFalse();
        assertThat(ProviderFailureClassifier.isRefusal(providerAnswer(500, "agreement not found or not valid"))).isFalse();
    }

    @Test
    void theWordingOutsideAProviderAnswerIsNotARefusal() {
        assertThat(ProviderFailureClassifier.isRefusal("agreement not found or not valid")).isFalse();
    }

    @Test
    void aStatusCodeInTheUrlIsNotTheProviderStatus() {
        var detail = "Counter Party responded with Response{protocol=h2, code=400, message=, url=https://p.example/x?code=409}. Body: bad.";

        assertThat(ProviderFailureClassifier.isRefusal(detail)).isFalse();
    }

    @Test
    void failuresWithoutAProviderSentenceAreNotRefusals() {
        assertThat(ProviderFailureClassifier.isRefusal("provider terminated")).isFalse();
        assertThat(ProviderFailureClassifier.isRefusal("")).isFalse();
        assertThat(ProviderFailureClassifier.isRefusal(null)).isFalse();
    }
}
