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

package org.niis.xroad.configuration.migration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyConfigValueRulesTest {

    @Test
    void deriveMatchingRuleReturnsDerivedEntry() {
        Map<String, Object> result = LegacyConfigValueRules.derive("proxy.health-check-port", "5588");

        assertThat(result).hasSize(1);
        assertThat(result).containsEntry("proxy.health-check-enabled", Boolean.TRUE);
    }

    @Test
    void deriveValueIsZeroReturnsEmpty() {
        Map<String, Object> result = LegacyConfigValueRules.derive("proxy.health-check-port", "0");

        assertThat(result).isEmpty();
    }

    @Test
    void deriveValueIsZeroWithWhitespaceReturnsEmpty() {
        Map<String, Object> result = LegacyConfigValueRules.derive("proxy.health-check-port", "  0  ");

        assertThat(result).isEmpty();
    }

    @Test
    void deriveValueIsNullReturnsEmpty() {
        Map<String, Object> result = LegacyConfigValueRules.derive("proxy.health-check-port", null);

        assertThat(result).isEmpty();
    }

    @Test
    void deriveValueIsEmptyStringReturnsEmpty() {
        Map<String, Object> result = LegacyConfigValueRules.derive("proxy.health-check-port", "");

        assertThat(result).isEmpty();
    }

    @Test
    void deriveUnknownKeyReturnsEmpty() {
        Map<String, Object> result = LegacyConfigValueRules.derive("some.other.key", "anything");

        assertThat(result).isEmpty();
    }

}
