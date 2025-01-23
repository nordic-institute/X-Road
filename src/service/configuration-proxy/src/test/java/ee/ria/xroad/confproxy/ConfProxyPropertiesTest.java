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
package ee.ria.xroad.confproxy;


import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;

import static ee.ria.xroad.common.SystemProperties.CONFIGURATION_PROXY_ADDRESS;
import static ee.ria.xroad.common.SystemProperties.CONFIGURATION_PROXY_CONF_PATH;
import static ee.ria.xroad.common.SystemProperties.DEFAULT_CONNECTOR_HOST;
import static org.assertj.core.api.Assertions.assertThat;

public class ConfProxyPropertiesTest {

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();
    private ConfProxyProperties proxyProperties;

    @Before
    public void initProxyProperties() throws ConfigurationException {
        System.setProperty(CONFIGURATION_PROXY_CONF_PATH, "src/test/resources/conf-proxy-conf");
        proxyProperties = new ConfProxyProperties("PROXY1");
    }

    @Test
    public void getConfigurationProxyURLsWhenAddressNotSet() throws Exception {
        System.setProperty(CONFIGURATION_PROXY_ADDRESS, DEFAULT_CONNECTOR_HOST);

        assertThat(proxyProperties.getConfigurationProxyURLs()).isEmpty();
    }

    @Test
    public void getConfigurationProxyURLsWhenAddressIsSet() throws Exception {
        System.setProperty(CONFIGURATION_PROXY_ADDRESS, "proxy");

        assertThat(proxyProperties.getConfigurationProxyURLs())
                .containsExactly("http://proxy/PROXY1", "https://proxy/PROXY1");
    }
}
