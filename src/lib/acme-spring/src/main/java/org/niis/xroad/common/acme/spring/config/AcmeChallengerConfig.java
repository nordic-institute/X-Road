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

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Registers the shared HTTP-01 challenge-response port-80 Tomcat connector. Kept separate from any
 * individual ACME scheduling config so that multiple independent {@link CertificateRenewalScheduler}
 * instances (auth/sign, DS TLS) can coexist without each registering its own connector on the same port.
 */
@Slf4j
@Configuration
public class AcmeChallengerConfig {

    @Bean
    @Profile("nontest")
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> acmeChallengeCustomizer(AcmeConfig acmeConfig) {
        if (acmeConfig.isAcmeChallengePortEnabled()) {
            return factory -> {
                var connector = new Connector(Http11NioProtocol.class.getName());
                int acmeChallengePort = acmeConfig.getAcmeChallengePort();
                connector.setScheme("http");
                connector.setPort(acmeChallengePort);
                log.info("ACME challenge port enabled, listening on port {}", acmeChallengePort);
                factory.addAdditionalConnectors(connector);
            };
        } else {
            log.info("ACME challenge port is disabled");
            return _ -> {
                // no-op
            };
        }
    }
}
