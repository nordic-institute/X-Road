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

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;

import static ee.ria.xroad.common.SystemProperties.AllowedFederationMode.ALL;
import static ee.ria.xroad.common.SystemProperties.AllowedFederationMode.NONE;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link FederationConfigurationSourceFilter}
 */
public class FederationConfigurationSourceFilterImplTest {

    @Rule
    public final RestoreSystemProperties restoreSystemProperties
            = new RestoreSystemProperties();


    private static final String FILTER_SEPARATOR = ",";

    private static final String DEFAULT_OWN_INSTANCE = "aaabbbccc";


    @Test
    public void shouldNotAllowAnyWhenNotConfigured() {
        System.clearProperty(SystemProperties.CONFIGURATION_CLIENT_ALLOWED_FEDERATIONS);
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("EE"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
    }


    @Test
    public void shouldNotAllowAnyWhenEmptyFilterList() {
        setFilter(" ");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("EE"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("fi-prod"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
    }

    @Test
    public void shouldNotAllowAnyWhenConfigured() {
        setFilter(NONE.name());
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("JE"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("fi-test"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
    }

    @Test
    public void shouldNotAllowAnyWhenConfiguredWithMixedCase() {
        setFilter("nOnE");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("test"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("EE-test"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
    }

    @Test
    public void shouldNotAllowAnyWhenConfiguredWithNoneAndAllAndCustomInstances() {
        buildAndSetFilter("fi-prod", "aLL", "EE", "nOne");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("any"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("does not matter"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("EE"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("fi-prod"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
    }

    @Test
    public void shouldAllowAllWhenConfigured() {
        setFilter(ALL.name());
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("EE"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("fi-prod"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
    }

    @Test
    public void shouldAllowAllWhenConfiguredWithMixedCase() {
        setFilter("aLl");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("FI"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("fi-TEST"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
    }

    @Test
    public void shouldAllowAllWhenConfiguredWithAllAndCustomInstances() {
        buildAndSetFilter("fi-prod", "aLL", "EE");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("any"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("does not matter"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));

    }

    @Test
    public void shouldOnlyAllowCustomInstancesWhenConfiguredWithMixedCase() {
        buildAndSetFilter("fi-prod", "EE");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("FI-PROd"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("ee"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("EE-TEST"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
    }

    @Test
    public void shouldAllowCustomInstances() {
        setFilter("fi-test,ee,some");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("FI-test"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("EE"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("some"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
    }

    @Test
    public void shouldParseFilterWithExtraSpaces() {
        setFilter("fi-prOD  ,    ee , fi-test, ");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("FI-PROd"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("EE"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("fi-TEST"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("ee-TEST"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(" "), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("  "), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
    }

    @Test
    public void shouldAlwaysAllowOwnInstance() {
        final String own = "fi-dev";
        setFilter(NONE.name());
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(own);
        assertThat(filter.shouldDownloadConfigurationFor(" "), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("  "), is(false));
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(own), is(true));
    }

    @Test
    public void shouldWorkWithSomeSpecialCharacters() {
        buildAndSetFilter("ää-ÖÖÖ", "èé-ãâ");
        FederationConfigurationSourceFilter filter = new FederationConfigurationSourceFilterImpl(DEFAULT_OWN_INSTANCE);
        assertThat(filter.shouldDownloadConfigurationFor("ÄÄ-ööö"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("ÈÉ-ÃÂ"), is(true));
        assertThat(filter.shouldDownloadConfigurationFor("dev-fi"), is(false));
        assertThat(filter.shouldDownloadConfigurationFor(DEFAULT_OWN_INSTANCE), is(true));
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
