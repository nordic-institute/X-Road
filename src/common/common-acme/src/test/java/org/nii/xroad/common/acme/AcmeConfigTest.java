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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.acme.AcmeCommonConfig;
import org.niis.xroad.common.acme.AcmeConfig;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AcmeConfigTest {
    private AcmeCommonConfig acmeCommonConfig;

    private ListAppender<ILoggingEvent> appender;
    private final Logger logger = (Logger) LoggerFactory.getLogger(AcmeCommonConfig.class);
    private final AcmeConfig config = mock(AcmeConfig.class);

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
        when(config.isAcmeChallengePortEnabled()).thenReturn(true);

        acmeCommonConfig.acmeProperties(config, "");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.getFirst().getMessage()).isEqualTo("Failed to load yaml configuration from {}");
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
        assertThat(appender.list.getFirst().getMessage()).isEqualTo("Acme challenge port enabled, but configuration is missing.");
    }

    @Test
    void testAcmePropertiesBeanIsCreatedWhenPropNotSet() {
        when(config.isAcmeChallengePortEnabled()).thenReturn(true);

        assertNotNull(acmeCommonConfig.acmeProperties(config, null));
        assertNotNull(acmeCommonConfig.acmeProperties(config, ""));
        assertNotNull(acmeCommonConfig.acmeProperties(config, "not valid"));

    }

    @Test
    void whenProxyUiApiAcmeChallengePortNotEnabledAndAcmeYmlNotExists() {
        when(config.isAcmeChallengePortEnabled()).thenReturn(false);

        acmeCommonConfig.acmeProperties(config, null);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.getFirst().getMessage())
                .isEqualTo("Acme configuration not set, and acme challenge port not enabled. Skipping ACME configuration.");
    }

}
