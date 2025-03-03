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

package org.niis.xroad.common.properties;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.List;

@ConfigMapping(prefix = CommonRpcProperties.PREFIX)
public interface CommonRpcProperties {
    String PREFIX = "xroad.common.rpc";
    String DEFAULT_USE_TLS = "false";

    @WithName("use-tls")
    @WithDefault(DEFAULT_USE_TLS)
    boolean useTls();

    @WithName("certificate-provisioning")
    CertificateProvisionProperties certificateProvisioning();

    interface CertificateProvisionProperties {
        @WithName("issuance-role-name")
        String issuanceRoleName();

        @WithName("common-name")
        String commonName();

        @WithName("alt-names")
        @WithDefault("[]")
        List<String> altNames();

        @WithName("ip-subject-alt-names")
        @WithDefault("[]")
        List<String> ipSubjectAltNames();

        @WithName("ttl-minutes")
        int ttlMinutes();

        @WithName("refresh-interval-minutes")
        int refreshIntervalMinutes();

        @WithName("secret-store-pki-path")
        String secretStorePkiPath();
    }
}
