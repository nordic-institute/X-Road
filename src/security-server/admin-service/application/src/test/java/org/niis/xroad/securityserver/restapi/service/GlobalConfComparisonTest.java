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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.ABSENT;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.DIFFERENT;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.EQUAL;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.STALE;

class GlobalConfComparisonTest {

    private static final String HELD = "did:web:ss.example.test%3A7183:xrd-ss0-m01";
    private static final String OTHER = "did:web:other.example.test%3A7183:xrd-ss0-m01";

    @Test
    void noCarriedValueIsAbsent() {
        assertThat(GlobalConfComparison.classify(HELD, null, _ -> false)).isEqualTo(ABSENT);
        assertThat(GlobalConfComparison.classify(HELD, null, _ -> true)).isEqualTo(ABSENT);
    }

    @Test
    void theHeldValueCarriedUnchangedIsEqual() {
        assertThat(GlobalConfComparison.classify(HELD, HELD, _ -> false)).isEqualTo(EQUAL);
    }

    @Test
    void anotherCarriedValueIsDifferent() {
        assertThat(GlobalConfComparison.classify(HELD, OTHER, _ -> false)).isEqualTo(DIFFERENT);
    }

    @Test
    void aStaleCarriedValueIsStaleEvenThoughItDiffers() {
        assertThat(GlobalConfComparison.classify(HELD, OTHER, OTHER::equals)).isEqualTo(STALE);
    }

    @Test
    void theHeldValueIsEqualEvenWhenTheStalenessTestRejectsIt() {
        assertThat(GlobalConfComparison.classify(HELD, HELD, _ -> true)).isEqualTo(EQUAL);
    }
}
