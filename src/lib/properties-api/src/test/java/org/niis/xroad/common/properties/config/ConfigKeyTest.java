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

package org.niis.xroad.common.properties.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.properties.config.Validator.oneOf;

class ConfigKeyTest {

    @Test
    void buildPopulatesRecordFields() {
        var key = Scope.of("xroad.signer").integer("key-length").withDefaultValue(2048).build();

        assertThat(key.key()).isEqualTo("xroad.signer.key-length");
        assertThat(key.type()).isEqualTo(Integer.class);
        assertThat(key.convertedDefaultValue()).isEqualTo(2048);
    }

    @Test
    void subScopeShortKeyUsesFullPathButKeepsShortName() {
        var key = Scope.of("xroad.proxy").child("client-proxy").integer("client-http-port").withDefaultValue(8080).build();

        assertThat(key.key()).isEqualTo("xroad.proxy.client-proxy.client-http-port");
    }

    @Test
    void buildValidatesDeclaredDefaultAndThrowsNamingTheKey() {
        var builder = Scope.of("xroad.signer")
                .integer("key-length")
                .withValidator(oneOf(2048, 3072, 4096))
                .withDefaultValue(1234);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xroad.signer.key-length");
    }

    @Test
    void validDefaultPassesValidation() {
        var key = Scope.of("xroad.signer")
                .integer("key-length")
                .withValidator(oneOf(2048, 3072, 4096))
                .withDefaultValue(3072)
                .build();

        assertThat(key.convertedDefaultValue()).isEqualTo(3072);
    }

    @Test
    void absentDefaultIsAllowedAndNotValidated() {
        var key = Scope.of("xroad.common")
                .string("instance-country")
                .withValidator(Validator.nonEmpty())
                .build();

        assertThat(key.defaultValue()).isNull();
    }

    @Test
    void shortKeyMayContainDots() {
        var key = Scope.of("xroad.signer").string("auth.method").build();

        assertThat(key.key()).isEqualTo("xroad.signer.auth.method");
    }
}
