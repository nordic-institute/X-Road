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

package org.niis.xroad.signer.core.config;

import ee.ria.xroad.common.ServicePrioritizationStrategy;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Map;

import static java.lang.Math.max;

@ConfigMapping(prefix = "xroad.signer")
public interface SignerProperties {
    int MIN_SIGNER_KEY_LENGTH = 2048;

    @WithName("selfsigned-cert-digest-algorithm")
    @WithDefault("SHA-512")
    String selfsignedCertDigestAlgorithm();

    @WithName("csr-signature-digest-algorithm")
    @WithDefault("SHA-256")
    String csrSignatureDigestAlgorithm();

    @WithName("enforce-token-pin-policy")
    @WithDefault("false")
    boolean enforceTokenPinPolicy();

    @WithName("ocsp-response-retrieval-active")
    @WithDefault("false")
    boolean ocspResponseRetrievalActive();

    @WithName("ocsp-retry-delay")
    @WithDefault("60")
    int ocspRetryDelay();

    @WithName("ocsp-cache-path")
    @WithDefault("/var/cache/xroad/")
    String ocspCachePath();

    @WithName("ocsp-prioritization-strategy")
    @WithDefault("NONE")
    ServicePrioritizationStrategy ocspPrioritizationStrategy();

    @WithName("module-manager-update-interval")
    @WithDefault("60")
    int moduleManagerUpdateInterval();

    @WithName("soft-token-rsa-sign-mechanism")
    @WithDefault("CKM_RSA_PKCS")
    String softTokenRsaSignMechanism();

    @WithName("soft-token-ec-sign-mechanism")
    @WithDefault("CKM_ECDSA")
    String softTokenEcSignMechanism();

    @WithName("soft-token-pin-keystore-algorithm")
    @WithDefault("RSA")
    String softTokenPinKeystoreAlgorithm();

    @WithName("key-length")
    @WithDefault("2048")
    int keyLength();

    default int getKeyLength() {
        return max(MIN_SIGNER_KEY_LENGTH, keyLength());
    }

    @WithName("key-named-curve")
    @WithDefault("secp256r1")
    String keyNamedCurve();

    @WithName("modules")
    Map<String, ModuleProperties> modulesConfig();

}
