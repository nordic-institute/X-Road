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
package org.niis.xroad.opmonitor.core;

import org.junit.jupiter.api.Test;
import org.niis.xroad.opmonitor.api.OperationalDataTruncationPolicy;
import org.niis.xroad.opmonitor.core.jpa.entity.OperationalDataRecordEntity;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalDataTruncationPolicyTest {

    @Test
    void everyPersistedTextColumnHasATruncationRule() {
        var persistedTextFields = Arrays.stream(OperationalDataRecordEntity.class.getDeclaredFields())
                .filter(field -> field.getType() == String.class)
                .map(Field::getName)
                .toList();

        assertThat(OperationalDataTruncationPolicy.truncatedFields()).containsExactlyInAnyOrderElementsOf(persistedTextFields);
    }

    @Test
    void textFieldIsTruncatedTo255Characters() {
        assertThat(OperationalDataTruncationPolicy.truncate("messageUserId", "u".repeat(300))).hasSize(255);
    }

    @Test
    void faultStringIsTruncatedTo2048Characters() {
        assertThat(OperationalDataTruncationPolicy.truncate("faultString", "f".repeat(3000))).hasSize(2048);
        assertThat(OperationalDataTruncationPolicy.truncate("faultString", "f".repeat(2000))).hasSize(2000);
    }

    @Test
    void valueWithinLimitAndNullAreUnchanged() {
        assertThat(OperationalDataTruncationPolicy.truncate("serviceCode", "getRandom")).isEqualTo("getRandom");
        assertThat(OperationalDataTruncationPolicy.truncate("serviceCode", null)).isNull();
    }

    @Test
    void truncationIsIdempotent() {
        String once = OperationalDataTruncationPolicy.truncate("faultString", "€".repeat(5000));

        assertThat(OperationalDataTruncationPolicy.truncate("faultString", once)).isEqualTo(once);
    }
}
