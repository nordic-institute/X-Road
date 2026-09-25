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
package org.niis.xroad.common.agreementtoken;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgreementTokenScopeTest {

    @ParameterizedTest
    @CsvSource({
        "GET,/foo/bar,GET,/foo/bar,true",
        "GET,/foo/bar,get,/foo/bar,true",
        "GET,/foo/bar,POST,/foo/bar,false",
        "GET,/foo/bar,GET,/foo/baz,false",
        "'*',/foo/bar,DELETE,/foo/bar,true",
        "GET,/foo/*,GET,/foo/bar,true",
        "GET,/foo/*,GET,/foo/bar/baz,false",
        "GET,/foo/**,GET,/foo/bar/baz,true",
        "'*','**',DELETE,/anything/at/all,true"
    })
    void shouldMatchAccordingToAclGlobRules(String scopeMethod, String scopePath, String requestMethod, String requestPath,
                                            boolean expected) {
        var scope = new AgreementTokenScope(scopeMethod, scopePath);

        assertThat(scope.matches(requestMethod, requestPath)).isEqualTo(expected);
    }

    @Test
    void shouldRejectBlankMethod() {
        assertThatThrownBy(() -> new AgreementTokenScope(" ", "/foo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankPath() {
        assertThatThrownBy(() -> new AgreementTokenScope("GET", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAPathPatternThatDoesNotCompile() {
        assertThatThrownBy(() -> new AgreementTokenScope("GET", "/foo/*?"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
