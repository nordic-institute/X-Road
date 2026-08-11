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
package org.niis.xroad.common.vault.config;

import java.time.Duration;
import java.util.List;

/**
 * Constructible {@link CertificateProvisioningProperties} carrier — values supplied via the
 * canonical constructor rather than a framework config mapping. Lets callers build the
 * cert-provisioning config from any source (e.g. resolved {@code XRoadConfig} values) and reuse it
 * wherever a {@link CertificateProvisioningProperties} is consumed.
 *
 * @param issuanceRoleName   PKI issuance role name
 * @param commonName         certificate common name
 * @param altNames           DNS subject alternative names
 * @param ipSubjectAltNames  IP subject alternative names
 * @param ttl                certificate time-to-live
 * @param secretStorePkiPath secret store PKI mount path
 */
public record CertificateProvisioningConfig(
        String issuanceRoleName,
        String commonName,
        List<String> altNames,
        List<String> ipSubjectAltNames,
        Duration ttl,
        String secretStorePkiPath) implements CertificateProvisioningProperties {
}
