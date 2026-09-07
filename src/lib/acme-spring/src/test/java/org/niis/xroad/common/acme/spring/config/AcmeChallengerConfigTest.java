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
package org.niis.xroad.common.acme.spring.config;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.acme.config.AcmeChallengeProperties;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcmeChallengerConfigTest {

    @Mock
    private AcmeChallengeProperties acmeConfig;
    @Mock
    private TomcatServletWebServerFactory factory;

    private final AcmeChallengerConfig challengerConfig = new AcmeChallengerConfig();

    @BeforeEach
    void setUp() {
        lenient().when(acmeConfig.getAcmeChallengePort()).thenReturn(5987);
    }

    @Test
    void shouldNotAddAConnectorWhenTheChallengePortIsDisabled() {
        when(acmeConfig.isAcmeChallengePortEnabled()).thenReturn(false);

        challengerConfig.acmeChallengeCustomizer(acmeConfig).customize(factory);

        verify(factory, never()).addAdditionalConnectors(any());
    }

    @Test
    void shouldBindEveryInterfaceWhenNoBindAddressIsConfigured() {
        when(acmeConfig.isAcmeChallengePortEnabled()).thenReturn(true);
        when(acmeConfig.getAcmeChallengeBindAddress()).thenReturn(null);

        challengerConfig.acmeChallengeCustomizer(acmeConfig).customize(factory);

        assertThat(capturedConnector().getProperty("address")).isNull();
    }

    @Test
    void shouldBindOnlyTheConfiguredAddressWhenOneIsSet() throws Exception {
        when(acmeConfig.isAcmeChallengePortEnabled()).thenReturn(true);
        when(acmeConfig.getAcmeChallengeBindAddress()).thenReturn("127.0.0.1");

        challengerConfig.acmeChallengeCustomizer(acmeConfig).customize(factory);

        // Tomcat coerces the "address" property from the configured String into a real InetAddress.
        assertThat(capturedConnector().getProperty("address")).isEqualTo(InetAddress.getByName("127.0.0.1"));
    }

    private Connector capturedConnector() {
        ArgumentCaptor<Connector> captor = ArgumentCaptor.forClass(Connector.class);
        verify(factory).addAdditionalConnectors(captor.capture());
        return captor.getValue();
    }
}
