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
package org.nii.xroad.common.acme;

import ee.ria.xroad.common.SystemProperties;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.acme.AcmeCommonConfig;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AcmeConfigTest {
    private AcmeCommonConfig acmeCommonConfig;

    private ListAppender<ILoggingEvent> appender;
    private final Logger logger = (Logger) LoggerFactory.getLogger(AcmeCommonConfig.class);

    @BeforeEach
    public void setup() {
        acmeCommonConfig = new AcmeCommonConfig();

        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    public void teardown() {
        logger.detachAppender(appender);
        appender.stop();
    }


    @Test
    void whenProxyUiApiAcmeChallengePortEnabledAndAcmeYmlNotExists() {
        System.setProperty(SystemProperties.PROXY_UI_API_ACME_CHALLENGE_PORT_ENABLED, "true");

        acmeCommonConfig.acmeProperties("");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
        assertThat(appender.list.getFirst().getMessage()).isEqualTo("Acme challenge port enabled, but configuration is missing.");
    }

    @Test
    void testAcmePropertiesBeanIsCreatedWhenPropNotSet() {
        System.setProperty(SystemProperties.PROXY_UI_API_ACME_CHALLENGE_PORT_ENABLED, "true");

        assertNotNull(acmeCommonConfig.acmeProperties(null));
        assertNotNull(acmeCommonConfig.acmeProperties(""));
        assertNotNull(acmeCommonConfig.acmeProperties("not valid"));

    }

    @Test
    void whenProxyUiApiAcmeChallengePortNotEnabledAndAcmeYmlNotExists() {
        System.setProperty(SystemProperties.PROXY_UI_API_ACME_CHALLENGE_PORT_ENABLED, "false");

        acmeCommonConfig.acmeProperties(null);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.getFirst().getMessage())
                .isEqualTo("Acme configuration not set, and acme challenge port not enabled. Skipping ACME configuration.");
    }

}
