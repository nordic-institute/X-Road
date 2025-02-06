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
package org.niis.xroad.proxy.core.configuration;

import ee.ria.xroad.common.AddOnStatusDiagnostics;
import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.messagelog.AbstractLogManager;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.proxy.core.ProxyProperties;
import org.niis.xroad.proxy.core.healthcheck.HealthCheckPort;
import org.niis.xroad.proxy.core.healthcheck.HealthCheckPortImpl;
import org.niis.xroad.proxy.core.healthcheck.HealthChecks;
import org.niis.xroad.proxy.core.healthcheck.NoopHealthCheckPort;
import org.niis.xroad.proxy.core.messagelog.NullLogManager;

import java.util.Arrays;
import java.util.List;

public class ProxyDiagnosticsConfig {

    @ApplicationScoped
    BackupEncryptionStatusDiagnostics backupEncryptionStatusDiagnostics() {
        return new BackupEncryptionStatusDiagnostics(
                SystemProperties.isBackupEncryptionEnabled(),
                getBackupEncryptionKeyIds());
    }

    @ApplicationScoped
    AddOnStatusDiagnostics addOnStatusDiagnostics(AbstractLogManager logManager) {
        return new AddOnStatusDiagnostics(NullLogManager.class != logManager.getClass());
    }

    @ApplicationScoped
    static class HealthCheckPortInitializer {

        @ApplicationScoped
        @Startup
        HealthCheckPort healthCheckPort(ProxyProperties proxyProperties,
                                        HealthChecks healthChecks) throws Exception {
            if (proxyProperties.healthCheckPort() > 0) {
                HealthCheckPortImpl healthCheckPort = new HealthCheckPortImpl(healthChecks, proxyProperties);
                healthCheckPort.init();
                return healthCheckPort;
            } else {
                return new NoopHealthCheckPort();
            }
        }

//        public void dispose(@Disposes HealthCheckPort healthCheckPort) throws Exception {
//            if (healthCheckPort instanceof HealthCheckPortImpl impl) {
//                impl.destroy();
//            }
//        }
    }

    private static List<String> getBackupEncryptionKeyIds() {
        return Arrays.stream(StringUtils.split(
                        SystemProperties.getBackupEncryptionKeyIds(), ','))
                .map(String::trim)
                .toList();
    }
}
