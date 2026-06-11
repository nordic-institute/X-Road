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
package org.niis.xroad.signer.core.config;

import ee.ria.xroad.common.ServicePrioritizationStrategy;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.util.Map;

import static java.lang.Math.max;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.CSR_SIGNATURE_DIGEST_ALGORITHM;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.ENFORCE_TOKEN_PIN_POLICY;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.KEY_LENGTH;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.KEY_NAMED_CURVE;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.MODULES;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.MODULE_MANAGER_UPDATE_INTERVAL;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.OCSP_CACHE_PATH;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.OCSP_PRIORITIZATION_STRATEGY;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.OCSP_RESPONSE_RETRIEVAL_ACTIVE;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.OCSP_RETRY_DELAY;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.SELFSIGNED_CERT_DIGEST_ALGORITHM;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.SOFT_TOKEN_EC_SIGN_MECHANISM;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.SOFT_TOKEN_PIN_KEYSTORE_ALGORITHM;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.SOFT_TOKEN_RSA_SIGN_MECHANISM;

/** Signer core properties ({@code xroad.signer.*}). */
@RequiredArgsConstructor
public class SignerProperties {

    private static final int MIN_SIGNER_KEY_LENGTH = 2048;

    private final XRoadConfig xRoadConfig;

    /** @return digest algorithm for self-signed certificates */
    public String selfsignedCertDigestAlgorithm() {
        return xRoadConfig.value(SELFSIGNED_CERT_DIGEST_ALGORITHM);
    }

    /** @return digest algorithm for certificate signing requests */
    public String csrSignatureDigestAlgorithm() {
        return xRoadConfig.value(CSR_SIGNATURE_DIGEST_ALGORITHM);
    }

    /** @return whether token PIN policy enforcement is active */
    public boolean enforceTokenPinPolicy() {
        return xRoadConfig.value(ENFORCE_TOKEN_PIN_POLICY);
    }

    /** @return whether OCSP response retrieval is active */
    public boolean ocspResponseRetrievalActive() {
        return xRoadConfig.value(OCSP_RESPONSE_RETRIEVAL_ACTIVE);
    }

    /** @return OCSP retry delay in seconds */
    public int ocspRetryDelay() {
        return xRoadConfig.value(OCSP_RETRY_DELAY);
    }

    /** @return path to the OCSP response cache directory */
    public String ocspCachePath() {
        return xRoadConfig.value(OCSP_CACHE_PATH);
    }

    /** @return OCSP responder prioritization strategy */
    public ServicePrioritizationStrategy ocspPrioritizationStrategy() {
        return xRoadConfig.value(OCSP_PRIORITIZATION_STRATEGY);
    }

    /** @return module-manager reload interval in seconds */
    public int moduleManagerUpdateInterval() {
        return xRoadConfig.value(MODULE_MANAGER_UPDATE_INTERVAL);
    }

    /** @return RSA sign mechanism name for the software token */
    public String softTokenRsaSignMechanism() {
        return xRoadConfig.value(SOFT_TOKEN_RSA_SIGN_MECHANISM);
    }

    /** @return EC sign mechanism name for the software token */
    public String softTokenEcSignMechanism() {
        return xRoadConfig.value(SOFT_TOKEN_EC_SIGN_MECHANISM);
    }

    /** @return keystore algorithm for the software token PIN */
    public String softTokenPinKeystoreAlgorithm() {
        return xRoadConfig.value(SOFT_TOKEN_PIN_KEYSTORE_ALGORITHM);
    }

    /** @return raw configured key length (may be below the minimum) */
    public int keyLength() {
        return xRoadConfig.value(KEY_LENGTH);
    }

    /** @return effective key length, clamped to at least {@value MIN_SIGNER_KEY_LENGTH} */
    public int getKeyLength() {
        return max(MIN_SIGNER_KEY_LENGTH, keyLength());
    }

    /** @return named curve for EC keys */
    public String keyNamedCurve() {
        return xRoadConfig.value(KEY_NAMED_CURVE);
    }

    /** @return hardware module configurations keyed by module UID */
    public Map<String, SignerModuleConfig> modulesConfig() {
        return xRoadConfig.value(MODULES);
    }
}
