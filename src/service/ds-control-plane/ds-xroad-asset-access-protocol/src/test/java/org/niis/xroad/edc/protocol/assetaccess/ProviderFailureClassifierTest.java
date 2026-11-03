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

class ProviderFailureClassifierTest {

    private static String providerAnswer(int code, String body) {
        return ("Counter Party responded with Response{protocol=h2, code=%d, message=, "
                + "url=https://provider.example/dsp/transfers/request}. Body: %s.").formatted(code, body);
    }

    private static Stream<Arguments> details() {
        return Stream.of(
                arguments("a conflict", providerAnswer(409, "{\"message\":\"anything\"}"), true),
                arguments("a not found naming the agreement",
                        providerAnswer(404, "Cannot process TransferRequestMessage because agreement not found or not valid"), true),
                arguments("the agreement wording in another case",
                        providerAnswer(404, "Agreement Not Found Or Not Valid"), true),
                arguments("a bare not found", providerAnswer(404, "Not found"), false),
                arguments("a bad request", providerAnswer(400, "TransferType is not supported"), false),
                arguments("the agreement wording under a bad request",
                        providerAnswer(400, "agreement not found or not valid"), false),
                arguments("the agreement wording under a server error",
                        providerAnswer(500, "agreement not found or not valid"), false),
                arguments("the wording outside a provider answer", "agreement not found or not valid", false),
                arguments("a status code in the url",
                        "Counter Party responded with Response{protocol=h2, code=400, message=, "
                                + "url=https://p.example/x?code=409}. Body: bad.",
                        false),
                arguments("a failure without a provider sentence", "provider terminated", false),
                arguments("an empty detail", "", false),
                arguments("a missing detail", null, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("details")
    void classifiesTheDetailAsARefusalOrNot(String description, String detail, boolean refusal) {
        assertThat(ProviderFailureClassifier.isRefusal(detail)).isEqualTo(refusal);
    }
}
