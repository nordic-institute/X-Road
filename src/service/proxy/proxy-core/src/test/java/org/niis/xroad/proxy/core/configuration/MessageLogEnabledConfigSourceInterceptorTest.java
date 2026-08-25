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
package org.niis.xroad.proxy.core.configuration;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageLogEnabledConfigSourceInterceptorTest {

    private static final String PROPERTY_NAME = "xroad.proxy.message-log.enabled";

    @ParameterizedTest
    @CsvSource({
            "true, true",
            "True, true",
            "TRUE, true",
            "false, false",
            "False, false",
            "FALSE, false",
            "garbage, false"
    })
    void shouldNormalizeToCanonicalBooleanString(String rawValue, String expected) {
        SmallRyeConfig config = configWith(rawValue);

        assertThat(config.getValue(PROPERTY_NAME, String.class)).isEqualTo(expected);
    }

    @Test
    void shouldNotAffectOtherProperties() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of("some.other.property", "True"), "test"))
                .withInterceptors(new MessageLogEnabledConfigSourceInterceptor())
                .build();

        assertThat(config.getValue("some.other.property", String.class)).isEqualTo("True");
    }

    private static SmallRyeConfig configWith(String rawValue) {
        return new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(PROPERTY_NAME, rawValue), "test"))
                .withInterceptors(new MessageLogEnabledConfigSourceInterceptor())
                .build();
    }
}
