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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.AddOnStatusDiagnostics;
import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.util.healthcheck.HealthCheckPort;
import ee.ria.xroad.common.util.healthcheck.HealthChecks;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.signer.SignerRpcClient;

import org.niis.xroad.proxy.ProxyProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;


@Configuration
public class ProxyDiagnosticsConfig {

    @Bean
    BackupEncryptionStatusDiagnostics backupEncryptionStatusDiagnostics(ProxyProperties proxyProperties) {
        return new BackupEncryptionStatusDiagnostics(
                proxyProperties.isBackupEncryptionEnabled(),
                proxyProperties.getBackupEncryptionKeyids());
    }

    @Bean
    AddOnStatusDiagnostics addOnStatusDiagnostics(@Qualifier("messageLogEnabledStatus") Boolean messageLogEnabledStatus) {
        return new AddOnStatusDiagnostics(messageLogEnabledStatus);
    }

    @Bean
    @Conditional(HealthCheckEnabledCondition.class)
    HealthChecks healthChecks(GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider,
                              ServerConfProvider serverConfProvider,
                              SignerRpcClient signerRpcClient) {
        return new HealthChecks(globalConfProvider, keyConfProvider, serverConfProvider, signerRpcClient);
    }

    @Bean
    @Conditional(HealthCheckEnabledCondition.class)
    HealthCheckPort healthCheckPort(HealthChecks healthChecks) {
        return new HealthCheckPort(healthChecks);
    }

    static class HealthCheckEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return SystemProperties.isHealthCheckEnabled();
        }
    }

}
