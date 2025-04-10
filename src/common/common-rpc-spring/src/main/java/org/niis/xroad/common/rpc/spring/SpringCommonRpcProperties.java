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

package org.niis.xroad.common.rpc.spring;

import lombok.Setter;
import org.niis.xroad.common.properties.CommonRpcProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;


@Setter
@ConfigurationProperties(prefix = CommonRpcProperties.PREFIX)
public class SpringCommonRpcProperties implements CommonRpcProperties {

    private boolean useTls = Boolean.parseBoolean(DEFAULT_USE_TLS);
    private SpringCertificateProvisionProperties certificateProvisioning;

    @Override
    public boolean useTls() {
        return useTls;
    }

    @Override
    public CertificateProvisionProperties certificateProvisioning() {
        return certificateProvisioning;
    }

    @Setter
    static class SpringCertificateProvisionProperties implements CertificateProvisionProperties {

        private String issuanceRoleName;
        private String commonName;
        private List<String> altNames;
        private List<String> ipSubjectAltNames;
        private int ttlMinutes;
        private int refreshIntervalMinutes;
        private Duration retryDelay;
        private Double retryExponentialBackoffMultiplier;
        private int retryMaxAttempts;
        private String secretStorePkiPath;

        @Override
        public String issuanceRoleName() {
            return issuanceRoleName;
        }

        @Override
        public String commonName() {
            return commonName;
        }

        @Override
        public List<String> altNames() {
            return altNames;
        }

        @Override
        public List<String> ipSubjectAltNames() {
            return ipSubjectAltNames;
        }

        @Override
        public int ttlMinutes() {
            return ttlMinutes;
        }

        @Override
        public int refreshIntervalMinutes() {
            return refreshIntervalMinutes;
        }

        @Override
        public String secretStorePkiPath() {
            return secretStorePkiPath;
        }

        @Override
        public Duration retryDelay() {
            return retryDelay;
        }

        @Override
        public Double retryExponentialBackoffMultiplier() {
            return retryExponentialBackoffMultiplier;
        }

        @Override
        public int retryMaxAttempts() {
            return retryMaxAttempts;
        }
    }
}
