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
package org.niis.xroad.cs.admin.core.acme;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.TaskScheduler;

/**
 * Wires the dataspace TLS certificate's ACME enrollment scheduler and its HTTP-01 challenge responder connector.
 * <p>
 * The Central Server's DS stack is unconditionally installed and provisioned, so - unlike the Security Server's
 * equivalent - this connector has no "challenge port enabled" toggle of its own: it is always added, always bound
 * to loopback only. {@link AcmeChallengeFilter} is what actually restricts what answers on it.
 * <p>
 * <b>HA clusters: known race, not handled here.</b> The Central Server's documented HA mode runs multiple nodes
 * against one shared database, with {@code /etc/xroad} - including the ACME account keystore
 * ({@link AcmeConfig#ACME_ACCOUNT_KEYSTORE_PATH}) and challenge directory
 * ({@link AcmeConfig#ACME_CHALLENGE_PATH}) - <em>not</em> synchronized between nodes. {@link #dsTlsAcmeEnrollmentScheduler}
 * carries no node gate (unlike, say, a leader-election check), so every node that has the renewal kill-switch
 * active runs its own independent enrollment/renewal cycle against its own local account keystore and challenge
 * directory, racing the others for the single {@code tls/ds-https} vault slot. The Central Server has no
 * leader/primary concept to hang a proper fix on ({@code HAConfigStatus}/{@code ha-node-name} only self-reports
 * which node a process is, it elects nothing) - building one is out of scope here. Until a real fix lands, HA
 * operators must keep {@code xroad.admin-service.ds-tls-acme.renewal-active} enabled on exactly one node and set
 * it to {@code false} in that node's own (unsynchronized) {@code /etc/xroad/conf.d/local.ini} on every other node.
 */
@Slf4j
@Configuration
public class DsTlsAcmeBeanConfig {

    @Bean
    @Profile("nontest")
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> dsTlsAcmeChallengeConnectorCustomizer(AcmeConfig acmeConfig) {
        return factory -> {
            var connector = new Connector(Http11NioProtocol.class.getName());
            int challengePort = acmeConfig.getChallengePort();
            connector.setScheme("http");
            connector.setProperty("address", "127.0.0.1");
            connector.setPort(challengePort);
            log.info("Dataspace TLS ACME challenge responder listening on loopback port {}", challengePort);
            factory.addAdditionalConnectors(connector);
        };
    }

    @Order(Ordered.LOWEST_PRECEDENCE - 99)
    @Bean
    @Conditional(IsDsTlsAcmeEnrollmentActive.class)
    @Profile("!test")
    DsTlsAcmeEnrollmentScheduler dsTlsAcmeEnrollmentScheduler(DsTlsAcmeEnrollmentWorker dsTlsAcmeEnrollmentWorker,
                                                              TaskScheduler taskScheduler, AcmeConfig acmeConfig) {
        DsTlsAcmeEnrollmentScheduler scheduler = new DsTlsAcmeEnrollmentScheduler(dsTlsAcmeEnrollmentWorker, acmeConfig, taskScheduler);
        scheduler.init();
        return scheduler;
    }

    /**
     * The renewal kill-switch, backed by {@code xroad.admin-service.ds-tls-acme.renewal-active} (default
     * {@code true}). A new capability on the Central Server: since its DS stack is always installed and
     * provisioned, there is no other way to pause this background activity (the Security Server's equivalent has
     * none - its only off switch is disabling the DataSpace feature entirely).
     */
    @Slf4j
    public static class IsDsTlsAcmeEnrollmentActive implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            boolean isActive = Boolean.parseBoolean(context.getEnvironment()
                    .getProperty("xroad.admin-service.ds-tls-acme.renewal-active", "true"));
            if (!isActive) {
                log.info("Dataspace TLS certificate ACME renewal configured to be inactive, job auto-scheduling disabled");
            }
            return isActive;
        }
    }

}
