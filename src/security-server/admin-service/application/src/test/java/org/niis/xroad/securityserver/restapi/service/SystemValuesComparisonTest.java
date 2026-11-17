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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.globalconf.model.ServerSystemValues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.ABSENT;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.DIFFERENT;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.EQUAL;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.STALE;

class SystemValuesComparisonTest {

    private static final String DID = "did:web:ss.example.test%3A7183:system";
    private static final String URL = "https://ss.example.test:7181/dsp";
    private static final String OTHER_DID = "did:web:ss.example.test%3A8443:system";
    private static final String OTHER_URL = "https://ss.example.test:9999/dsp";

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = URL)
    void valuesThatGlobalconfDoesNotCarryAreAbsentWhetherOrNotTheDspBaseUrlCanBeBuilt(String builtDspBaseUrl) {
        assertThat(SystemValuesComparison.of(DID, builtDspBaseUrl, null, _ -> false))
                .isEqualTo(new SystemValuesComparison(ABSENT, ABSENT));
    }

    @Test
    void equalValuesAreEqualWhateverTheStalenessSays() {
        var comparison = SystemValuesComparison.of(DID, URL, new ServerSystemValues(DID, URL), _ -> true);

        assertThat(comparison).isEqualTo(new SystemValuesComparison(EQUAL, EQUAL));
    }

    @Test
    void eachValueIsComparedOnItsOwn() {
        var didOnly = SystemValuesComparison.of(DID, URL, new ServerSystemValues(OTHER_DID, URL), _ -> false);
        var urlOnly = SystemValuesComparison.of(DID, URL, new ServerSystemValues(DID, OTHER_URL), _ -> false);

        assertThat(didOnly).isEqualTo(new SystemValuesComparison(DIFFERENT, EQUAL));
        assertThat(urlOnly).isEqualTo(new SystemValuesComparison(EQUAL, DIFFERENT));
    }

    @Test
    void aStalePairMakesEveryDifferingValueStale() {
        var carried = new ServerSystemValues(OTHER_DID, OTHER_URL);

        assertThat(SystemValuesComparison.of(DID, URL, carried, pair -> pair.equals(carried)))
                .isEqualTo(new SystemValuesComparison(STALE, STALE));
        assertThat(SystemValuesComparison.of(DID, URL, new ServerSystemValues(DID, OTHER_URL), _ -> true))
                .isEqualTo(new SystemValuesComparison(EQUAL, STALE));
    }

    @Test
    void aDspBaseUrlThatCannotBeBuiltIsStaleWhileGlobalconfCarriesValues() {
        var comparison = SystemValuesComparison.of(DID, null, new ServerSystemValues(DID, URL), _ -> false);

        assertThat(comparison).isEqualTo(new SystemValuesComparison(EQUAL, STALE));
    }

    @Test
    void theOverallComparisonIsAbsentOnlyWhenNothingIsCarriedAndEqualOnlyWhenBothValuesAreEqual() {
        assertThat(new SystemValuesComparison(ABSENT, ABSENT).overall()).isEqualTo(ABSENT);
        assertThat(new SystemValuesComparison(EQUAL, EQUAL).overall()).isEqualTo(EQUAL);
        assertThat(new SystemValuesComparison(EQUAL, DIFFERENT).overall()).isEqualTo(DIFFERENT);
        assertThat(new SystemValuesComparison(DIFFERENT, EQUAL).overall()).isEqualTo(DIFFERENT);
    }

    @Test
    void theOverallComparisonIsStaleWhenEitherValueIsStale() {
        assertThat(new SystemValuesComparison(EQUAL, STALE).overall()).isEqualTo(STALE);
        assertThat(new SystemValuesComparison(STALE, DIFFERENT).overall()).isEqualTo(STALE);
        assertThat(new SystemValuesComparison(DIFFERENT, STALE).overall()).isEqualTo(STALE);
    }
}
