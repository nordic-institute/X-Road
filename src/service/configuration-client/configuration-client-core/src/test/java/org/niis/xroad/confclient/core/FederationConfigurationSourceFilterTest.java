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
package org.niis.xroad.confclient.core;

import org.junit.jupiter.api.Test;
import org.niis.xroad.confclient.core.config.ConfigurationClientProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FederationConfigurationSourceFilter}
 */
class FederationConfigurationSourceFilterImplTest {

    private static final String FILTER_SEPARATOR = ",";

    private static final String DEFAULT_OWN_INSTANCE = "aaabbbccc";

    @Test
    void shouldNotAllowAnyWhenNotConfigured() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE, "NONE");
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldNotAllowAnyWhenEmptyFilterList() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE, " ");
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("fi-prod")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldNotAllowAnyWhenConfigured() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE,
                ConfigurationClientProperties.AllowedFederationMode.NONE.name());
        assertThat(filter.shouldDownloadConfigurationFor("JE")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("fi-test")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldNotAllowAnyWhenConfiguredWithMixedCase() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE, "nOnE");
        assertThat(filter.shouldDownloadConfigurationFor("test")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("EE-test")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldNotAllowAnyWhenConfiguredWithNoneAndAllAndCustomInstances() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE,
                buildFilter("fi-prod", "aLL", "EE", "nOne"));
        assertThat(filter.shouldDownloadConfigurationFor("any")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("does not matter")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("fi-prod")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldAllowAllWhenConfigured() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE,
                ConfigurationClientProperties.AllowedFederationMode.ALL.name());
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("fi-prod")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldAllowAllWhenConfiguredWithMixedCase() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE, "aLl");
        assertThat(filter.shouldDownloadConfigurationFor("FI")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("fi-TEST")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldAllowAllWhenConfiguredWithAllAndCustomInstances() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE,
                buildFilter("fi-prod", "aLL", "EE"));
        assertThat(filter.shouldDownloadConfigurationFor("any")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("does not matter")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();

    }

    @Test
    void shouldOnlyAllowCustomInstancesWhenConfiguredWithMixedCase() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE,
                buildFilter("fi-prod", "EE"));
        assertThat(filter.shouldDownloadConfigurationFor("FI-PROd")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("ee")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("EE-TEST")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldAllowCustomInstances() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE,
                "fi-test,ee,some");
        assertThat(filter.shouldDownloadConfigurationFor("FI-test")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("some")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldParseFilterWithExtraSpaces() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE,
                "fi-prOD  ,    ee , fi-test, ");
        assertThat(filter.shouldDownloadConfigurationFor("FI-PROd")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("fi-TEST")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("ee-TEST")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(" ")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("  ")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldAlwaysAllowOwnInstance() {
        final String own = "fi-dev";
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(own,
                ConfigurationClientProperties.AllowedFederationMode.NONE.name());
        assertThat(filter.shouldDownloadConfigurationFor(" ")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("  ")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(own)).isTrue();
    }

    @Test
    void shouldWorkWithSomeSpecialCharacters() {
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE,
                buildFilter("ää-ÖÖÖ", "èé-ãâ"));
        assertThat(filter.shouldDownloadConfigurationFor("ÄÄ-ööö")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("ÈÉ-ÃÂ")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    private String buildFilter(String... elements) {
        StringBuilder b = new StringBuilder();
        for (String element : elements) {
            b.append(element).append(FILTER_SEPARATOR);
        }
        return b.toString();
    }

}
