/*
 * The MIT License
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

package org.niis.xroad.e2e.parity.acl;

import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.acl.AclScenario.Expectation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectationTest {

    private static final List<String> CODES = List.of("p-a", "p-b", "p-c");

    @Test
    void listedPositionsResolveToTheirServiceCodesAfterTheLiteralTexts() {
        var expectation = new Expectation(List.of("text"), List.of(), List.of(2, 0), List.of());

        assertThat(expectation.required(CODES)).containsExactly("text", "p-c", "p-a");
    }

    @Test
    void unlistedPositionsResolveToTheirServiceCodesAfterTheLiteralTexts() {
        var expectation = new Expectation(List.of(), List.of("hidden"), List.of(), List.of(1));

        assertThat(expectation.forbidden(CODES)).containsExactly("hidden", "p-b");
    }

    @Test
    void listedPositionsAreRequiredOnlyAndUnlistedPositionsAreForbiddenOnly() {
        var expectation = Expectation.listing(List.of(0), List.of(1, 2));

        assertThat(expectation.required(CODES)).containsExactly("p-a");
        assertThat(expectation.forbidden(CODES)).containsExactly("p-b", "p-c");
    }

    @Test
    void expectationWithoutPositionsNeedsNoServiceCodes() {
        var expectation = Expectation.masked("shown", "hidden");

        assertThat(expectation.required(List.of())).containsExactly("shown");
        assertThat(expectation.forbidden(List.of())).containsExactly("hidden");
        assertThat(Expectation.NONE.required(List.of())).isEmpty();
        assertThat(Expectation.NONE.forbidden(List.of())).isEmpty();
    }
}
