package org.niis.xroad.securityserver.restapi.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import ee.ria.xroad.common.SystemProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class AcmeConfigTest {
    private AcmeConfig acmeConfig;

    private ListAppender<ILoggingEvent> appender;
    private final Logger logger = (Logger) LoggerFactory.getLogger(AcmeConfig.class);

    @Before
    public void setup() {
        acmeConfig = new AcmeConfig();

        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @After
    public void teardown() {
        logger.detachAppender(appender);
        appender.stop();
    }


    @Test
    public void whenProxyUiApiAcmeChallengePortEnabledAndAcmeYmlNotExists() {
        System.setProperty(SystemProperties.PROXY_UI_API_ACME_CHALLENGE_PORT_ENABLED, "true");

        acmeConfig.acmeProperties();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getMessage()).isEqualTo("Failed to load yaml configuration from {}");
    }

    @Test
    public void whenProxyUiApiAcmeChallengePortNotEnabledAndAcmeYmlNotExists() {
        System.setProperty(SystemProperties.PROXY_UI_API_ACME_CHALLENGE_PORT_ENABLED, "false");

        acmeConfig.acmeProperties();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getMessage()).isEqualTo("Configuration {} not exists");

    }
}
