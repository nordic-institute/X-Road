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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.niis.xroad.edc.protocol.assetaccess.ProviderFailureClassifier.Classification.AGREEMENT_REFUSAL;
import static org.niis.xroad.edc.protocol.assetaccess.ProviderFailureClassifier.Classification.IDENTITY_REJECTION;
import static org.niis.xroad.edc.protocol.assetaccess.ProviderFailureClassifier.Classification.NO_REFUSAL;
import static org.niis.xroad.edc.protocol.assetaccess.ProviderFailureClassifier.Stage.CATALOG;
import static org.niis.xroad.edc.protocol.assetaccess.ProviderFailureClassifier.Stage.NEGOTIATION;
import static org.niis.xroad.edc.protocol.assetaccess.ProviderFailureClassifier.Stage.TRANSFER;

class ProviderFailureClassifierTest {

    private static String providerAnswer(int code, String body) {
        return ("Counter Party responded with Response{protocol=h2, code=%d, message=, "
                + "url=https://provider.example/dsp/transfers/request}. Body: %s.").formatted(code, body);
    }

    private static Stream<Arguments> details() {
        return Stream.of(
                arguments("a conflict", providerAnswer(409, "{\"message\":\"anything\"}"), TRANSFER, AGREEMENT_REFUSAL),
                arguments("a not found naming the agreement",
                        providerAnswer(404, "Cannot process TransferRequestMessage because agreement not found or not valid"),
                        TRANSFER, AGREEMENT_REFUSAL),
                arguments("a bad request naming the agreement",
                        providerAnswer(400, "Cannot process TransferRequestMessage because agreement not found or not valid"),
                        TRANSFER, AGREEMENT_REFUSAL),
                arguments("the agreement wording in another case",
                        providerAnswer(404, "Agreement Not Found Or Not Valid"), TRANSFER, AGREEMENT_REFUSAL),
                arguments("a bare not found", providerAnswer(404, "Not found"), TRANSFER, IDENTITY_REJECTION),
                arguments("a negotiation unauthorized", providerAnswer(401, "Unauthorized."), NEGOTIATION, NO_REFUSAL),
                arguments("a transfer unauthorized", providerAnswer(401, "Unauthorized."), TRANSFER, NO_REFUSAL),
                arguments("a catalog unauthorized", providerAnswer(401, "Unauthorized."), CATALOG, IDENTITY_REJECTION),
                arguments("a catalog unauthorized naming the agreement",
                        providerAnswer(401, "agreement not found or not valid"), CATALOG, IDENTITY_REJECTION),
                arguments("a catalog bare not found", providerAnswer(404, "Not found"), CATALOG, NO_REFUSAL),
                arguments("a catalog not found naming the agreement",
                        providerAnswer(404, "Cannot process CatalogRequestMessage because agreement not found or not valid"),
                        CATALOG, NO_REFUSAL),
                arguments("a bad request", providerAnswer(400, "TransferType is not supported"), TRANSFER, NO_REFUSAL),
                arguments("the agreement wording under a server error",
                        providerAnswer(500, "agreement not found or not valid"), TRANSFER, NO_REFUSAL),
                arguments("the agreement wording under a service unavailable",
                        providerAnswer(503, "agreement not found or not valid"), TRANSFER, NO_REFUSAL),
                arguments("the wording outside a provider answer", "agreement not found or not valid", TRANSFER, NO_REFUSAL),
                arguments("a status code in the url",
                        "Counter Party responded with Response{protocol=h2, code=400, message=, "
                                + "url=https://p.example/x?code=409}. Body: bad.",
                        TRANSFER, NO_REFUSAL),
                arguments("a failure without a provider sentence", "provider terminated", TRANSFER, NO_REFUSAL),
                arguments("an empty detail", "", TRANSFER, NO_REFUSAL),
                arguments("a missing detail", null, TRANSFER, NO_REFUSAL));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("details")
    void classifiesTheDetail(String description, String detail, ProviderFailureClassifier.Stage stage,
                              ProviderFailureClassifier.Classification classification) {
        assertThat(ProviderFailureClassifier.classify(detail, stage)).isEqualTo(classification);
    }
}
