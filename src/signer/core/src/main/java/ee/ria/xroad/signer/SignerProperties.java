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

package ee.ria.xroad.signer;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "xroad.signer")
public interface SignerProperties {

    @WithName("device-configuration-file")//: /etc/xroad/devices.ini
    String deviceConfigurationFile();

    @WithName("key-configuration-file")//: /etc/xroad/signer/keyconf.xml
    String keyConfigurationFile();

    @WithName("selfsigned-cert-digest-algorithm")//: SHA-512
    String selfsignedCertDigestAlgorithm();

    @WithName("csr-signature-digest-algorithm")//: SHA-256
    String csrSignatureDigestAlgorithm();

    @WithName("enforce-token-pin-policy")//: false
    boolean enforceTokenPinPolicy();

    @WithName("ocsp-response-retrieval-active")
    boolean ocspResponseRetrievalActive();

    @WithName("ocsp-retry-delay")
    int ocspRetryDelay();

    @WithName("ocsp-cache-path")//: /var/cache/xroad
    String ocspCachePath();

    @WithName("module-manager-update-interval")//: 60
    int moduleManagerUpdateInterval();

    @WithName("soft-token-rsa-sign-mechanism")
    String softTokenRsaSignMechanism();

    @WithName("soft-token-ec-sign-mechanism")
    String softTokenEcSignMechanism();

    @WithName("soft-token-pin-keystore-algorithm")
    String softTokenPinKeystoreAlgorithm();
}
