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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatorTest {

    @Test
    void positiveAcceptsPositiveDuration() {
        assertThat(Validator.positive().validate(Duration.ofSeconds(1)).valid()).isTrue();
    }

    @Test
    void positiveRejectsZeroNegativeAndNull() {
        var positive = Validator.positive();

        assertThat(positive.validate(Duration.ZERO).valid()).isFalse();
        assertThat(positive.validate(Duration.ofSeconds(-1)).valid()).isFalse();
        assertThat(positive.validate(null).valid()).isFalse();
    }

    @Test
    void positiveRejectionCarriesMessage() {
        var result = Validator.positive().validate(Duration.ZERO);

        assertThat(result.message()).isEqualTo("must be a positive duration");
    }

    @Test
    void negativeDurationDefaultWithPositiveFailsAtBuild() {
        var builder = Prefix.of("xroad.signer")
                .keyDuration("csr-timeout")
                .withValidator(Validator.positive())
                .withDefaultValue(Duration.ofSeconds(-1));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xroad.signer.csr-timeout");
    }

    @Test
    void positiveDurationDefaultBuildsCleanly() {
        var key = Prefix.of("xroad.signer")
                .keyDuration("csr-timeout")
                .withValidator(Validator.positive())
                .withDefaultValue(Duration.ofSeconds(30))
                .build();

        assertThat(key.convertedDefaultValue()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void builtInValidatorsDescribeTheirConstraint() {
        assertThat(Validator.oneOf(2048, 3072).describe().orElseThrow()).startsWith("one of").contains("2048", "3072");
        assertThat(Validator.range(1, 10).describe()).contains("within [1, 10]");
        assertThat(Validator.pattern("[a-z]+").describe()).contains("matches [a-z]+");
        assertThat(Validator.nonEmpty().describe()).contains("non-empty");
        assertThat(Validator.positive().describe()).contains("positive duration");
    }

    @Test
    void noneHasNoConstraintSummary() {
        assertThat(Validator.none().describe()).isEmpty();
    }

    @Test
    void andJoinsBothConstraintSummaries() {
        var combined = Validator.and(Validator.nonEmpty(), Validator.pattern("[a-z]+"));

        assertThat(combined.describe()).contains("non-empty and matches [a-z]+");
    }

    @Test
    void orJoinsBothConstraintSummaries() {
        var combined = Validator.or(Validator.nonEmpty(), Validator.pattern("[a-z]+"));

        assertThat(combined.describe()).contains("non-empty or matches [a-z]+");
    }
}
