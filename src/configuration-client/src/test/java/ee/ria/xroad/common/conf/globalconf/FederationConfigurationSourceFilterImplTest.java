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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.SystemProperties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static ee.ria.xroad.common.SystemProperties.AllowedFederationMode.ALL;
import static ee.ria.xroad.common.SystemProperties.AllowedFederationMode.NONE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FederationConfigurationSourceFilter}
 */
class FederationConfigurationSourceFilterImplTest {

    private static final String FILTER_SEPARATOR = ",";

    private static final String DEFAULT_OWN_INSTANCE = "aaabbbccc";

    private static String initialAllowedFederations;

    @BeforeAll
    static void saveAllowedFederations() {
        initialAllowedFederations = System.getProperty(SystemProperties.CONFIGURATION_CLIENT_ALLOWED_FEDERATIONS);
    }

    @AfterAll
    static void restoreAllowedFederations() {
        if (initialAllowedFederations == null) {
            System.clearProperty(SystemProperties.CONFIGURATION_CLIENT_ALLOWED_FEDERATIONS);
        } else {
            System.setProperty(SystemProperties.CONFIGURATION_CLIENT_ALLOWED_FEDERATIONS, initialAllowedFederations);
        }
    }

    @Test
    void shouldNotAllowAnyWhenNotConfigured() {
        System.clearProperty(SystemProperties.CONFIGURATION_CLIENT_ALLOWED_FEDERATIONS);
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }


    @Test
    void shouldNotAllowAnyWhenEmptyFilterList() {
        setFilter(" ");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("fi-prod")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldNotAllowAnyWhenConfigured() {
        setFilter(NONE.name());
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("JE")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("fi-test")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldNotAllowAnyWhenConfiguredWithMixedCase() {
        setFilter("nOnE");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("test")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("EE-test")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldNotAllowAnyWhenConfiguredWithNoneAndAllAndCustomInstances() {
        buildAndSetFilter("fi-prod", "aLL", "EE", "nOne");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("any")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("does not matter")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("fi-prod")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldAllowAllWhenConfigured() {
        setFilter(ALL.name());
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("fi-prod")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldAllowAllWhenConfiguredWithMixedCase() {
        setFilter("aLl");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("FI")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("fi-TEST")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldAllowAllWhenConfiguredWithAllAndCustomInstances() {
        buildAndSetFilter("fi-prod", "aLL", "EE");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("any")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("does not matter")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();

    }

    @Test
    void shouldOnlyAllowCustomInstancesWhenConfiguredWithMixedCase() {
        buildAndSetFilter("fi-prod", "EE");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("FI-PROd")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("ee")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("EE-TEST")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldAllowCustomInstances() {
        setFilter("fi-test,ee,some");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("FI-test")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("EE")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("some")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    @Test
    void shouldParseFilterWithExtraSpaces() {
        setFilter("fi-prOD  ,    ee , fi-test, ");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
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
        setFilter(NONE.name());
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(own);
        assertThat(filter.shouldDownloadConfigurationFor(" ")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("  ")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(own)).isTrue();
    }

    @Test
    void shouldWorkWithSomeSpecialCharacters() {
        buildAndSetFilter("ää-ÖÖÖ", "èé-ãâ");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("ÄÄ-ööö")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("ÈÉ-ÃÂ")).isTrue();
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi")).isFalse();
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE)).isTrue();
    }

    private void setFilter(String filter) {
        System.setProperty(SystemProperties.CONFIGURATION_CLIENT_ALLOWED_FEDERATIONS, filter);
    }

    private void buildAndSetFilter(String... elements) {
        StringBuilder b = new StringBuilder();
        for (String element : elements) {
            b.append(element).append(FILTER_SEPARATOR);
        }
        setFilter(b.toString());
    }

}
