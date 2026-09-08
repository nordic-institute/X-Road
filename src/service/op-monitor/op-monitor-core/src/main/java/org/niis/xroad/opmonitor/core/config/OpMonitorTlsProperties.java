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
package org.niis.xroad.opmonitor.core.config;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.vault.config.CertificateProvisioningConfig;
import org.niis.xroad.common.vault.config.CertificateProvisioningProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.CERT_PROVISIONING_ALT_NAMES;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.CERT_PROVISIONING_COMMON_NAME;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.CERT_PROVISIONING_ISSUANCE_ROLE_NAME;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.CERT_PROVISIONING_SECRET_STORE_PKI_PATH;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.CERT_PROVISIONING_TTL;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.TLS_CLIENT_CERTIFICATE_REFRESH_INTERVAL;

@RequiredArgsConstructor
public class OpMonitorTlsProperties {

    private final XRoadConfig xRoadConfig;

    public CertificateProvisioningProperties certificateProvisioning() {
        return new CertificateProvisioningConfig(
                xRoadConfig.value(CERT_PROVISIONING_ISSUANCE_ROLE_NAME),
                xRoadConfig.value(CERT_PROVISIONING_COMMON_NAME),
                toList(xRoadConfig.value(CERT_PROVISIONING_ALT_NAMES)),
                toList(xRoadConfig.value(CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES)),
                xRoadConfig.value(CERT_PROVISIONING_TTL),
                xRoadConfig.value(CERT_PROVISIONING_SECRET_STORE_PKI_PATH));
    }

    public Duration clientCertificateRefreshInterval() {
        return xRoadConfig.value(TLS_CLIENT_CERTIFICATE_REFRESH_INTERVAL);
    }

    private static List<String> toList(String[] values) {
        return Arrays.stream(values).filter(value -> !value.isBlank()).toList();
    }

}
