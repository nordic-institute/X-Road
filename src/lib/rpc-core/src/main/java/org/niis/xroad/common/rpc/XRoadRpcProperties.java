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
package org.niis.xroad.common.rpc;

import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.vault.config.CertificateProvisioningProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** {@link RpcProperties} implementation backed by the XRoadConfig DSL. */
public class XRoadRpcProperties implements RpcProperties {

    private final XRoadConfig config;
    private final CertProvisioningImpl certProvisioning;

    public XRoadRpcProperties(XRoadConfig config) {
        this.config = config;
        this.certProvisioning = new CertProvisioningImpl(config);
    }

    @Override
    public boolean useTls() {
        return config.value(CommonRpcConfigKeys.USE_TLS);
    }

    @Override
    public RpcCertificateProvisioningProperties certificateProvisioning() {
        return certProvisioning;
    }

    /** DSL-backed implementation of {@link RpcCertificateProvisioningProperties}. */
    public static final class CertProvisioningImpl
            implements RpcCertificateProvisioningProperties, CertificateProvisioningProperties {

        private final XRoadConfig config;

        public CertProvisioningImpl(XRoadConfig config) {
            this.config = config;
        }

        @Override
        public String issuanceRoleName() {
            return config.value(CommonRpcConfigKeys.CERT_PROVISIONING_ISSUANCE_ROLE_NAME);
        }

        @Override
        public String commonName() {
            return config.value(CommonRpcConfigKeys.CERT_PROVISIONING_COMMON_NAME);
        }

        @Override
        public List<String> altNames() {
            return Arrays.asList(config.value(CommonRpcConfigKeys.CERT_PROVISIONING_ALT_NAMES));
        }

        @Override
        public List<String> ipSubjectAltNames() {
            return Arrays.asList(config.value(CommonRpcConfigKeys.CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES));
        }

        @Override
        public Duration ttl() {
            return config.value(CommonRpcConfigKeys.CERT_PROVISIONING_TTL);
        }

        @Override
        public String secretStorePkiPath() {
            return config.value(CommonRpcConfigKeys.CERT_PROVISIONING_SECRET_STORE_PKI_PATH);
        }

        @Override
        public Duration refreshInterval() {
            return config.value(CommonRpcConfigKeys.CERT_PROVISIONING_REFRESH_INTERVAL);
        }

        @Override
        public Duration retryDelay() {
            return config.value(CommonRpcConfigKeys.CERT_PROVISIONING_RETRY_DELAY);
        }

        @Override
        public Double retryExponentialBackoffMultiplier() {
            return Double.parseDouble(config.value(CommonRpcConfigKeys.CERT_PROVISIONING_RETRY_EXPONENTIAL_BACKOFF_MULTIPLIER));
        }

        @Override
        public int retryMaxAttempts() {
            return config.value(CommonRpcConfigKeys.CERT_PROVISIONING_RETRY_MAX_ATTEMPTS);
        }

        @Override
        public Duration retryTimeout() {
            return config.value(CommonRpcConfigKeys.CERT_PROVISIONING_RETRY_TIMEOUT);
        }
    }
}
