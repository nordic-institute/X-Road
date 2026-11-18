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

package org.niis.xroad.edc.issuerdata;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BitstringStatusListKindTest {

    private FakeIssuer issuer;
    private BitstringStatusListKind kind;

    @BeforeEach
    void setUp() {
        issuer = new FakeIssuer();
        issuer.publishStatusList(Set.of(), null);
        var stack = EdcTestStack.create();
        kind = new BitstringStatusListKind(stack.mapper(), List.of("*/*"), stack.httpClient(),
                stack.tokenValidationService(), stack.didPublicKeyResolver());
    }

    @AfterEach
    void tearDown() {
        issuer.close();
    }

    @Test
    void shouldReportRetryableFailureWithoutIssuerResponseBodyWhenIssuerAnswers503() {
        issuer.down(true);

        var result = kind.download(issuer.statusListUrl());

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getFailureMessages()).isNotEmpty().noneMatch(message -> message.contains(FakeIssuer.ERROR_BODY));
    }
}
