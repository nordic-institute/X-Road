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
package org.niis.xroad.edc.extension.jwssigner;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.signer.SignerProxy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.impl.BaseJWSProvider;
import com.nimbusds.jose.util.Base64URL;

import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class XRoadJWSSigner extends BaseJWSProvider implements JWSSigner {

    private static final Set<JWSAlgorithm> SUPPORTED_ALGORITHMS;
    private static final Map<JWSAlgorithm, DigestAlgorithm> JWS_TO_HASH_ALGORITHM_MAP;

    static {
        Set<JWSAlgorithm> supportedAlgorithms = new LinkedHashSet<>();
        supportedAlgorithms.add(JWSAlgorithm.RS256);
        supportedAlgorithms.add(JWSAlgorithm.RS384);
        supportedAlgorithms.add(JWSAlgorithm.RS512);
        supportedAlgorithms.add(JWSAlgorithm.PS256);
        supportedAlgorithms.add(JWSAlgorithm.PS384);
        supportedAlgorithms.add(JWSAlgorithm.PS512);
        supportedAlgorithms.add(JWSAlgorithm.ES256);
        supportedAlgorithms.add(JWSAlgorithm.ES384);
        supportedAlgorithms.add(JWSAlgorithm.ES512);
        SUPPORTED_ALGORITHMS = Collections.unmodifiableSet(supportedAlgorithms);

        Map<JWSAlgorithm, DigestAlgorithm> jwsToHashAlgorithmMap = new HashMap<>();
        jwsToHashAlgorithmMap.put(JWSAlgorithm.RS256, DigestAlgorithm.SHA256);
        jwsToHashAlgorithmMap.put(JWSAlgorithm.RS384, DigestAlgorithm.SHA384);
        jwsToHashAlgorithmMap.put(JWSAlgorithm.RS512, DigestAlgorithm.SHA512);
        jwsToHashAlgorithmMap.put(JWSAlgorithm.PS256, DigestAlgorithm.SHA256);
        jwsToHashAlgorithmMap.put(JWSAlgorithm.PS384, DigestAlgorithm.SHA384);
        jwsToHashAlgorithmMap.put(JWSAlgorithm.PS512, DigestAlgorithm.SHA512);
        jwsToHashAlgorithmMap.put(JWSAlgorithm.ES256, DigestAlgorithm.SHA256);
        jwsToHashAlgorithmMap.put(JWSAlgorithm.ES384, DigestAlgorithm.SHA384);
        jwsToHashAlgorithmMap.put(JWSAlgorithm.ES512, DigestAlgorithm.SHA512);
        JWS_TO_HASH_ALGORITHM_MAP = Collections.unmodifiableMap(jwsToHashAlgorithmMap);
    }

    private final String keyId;

    public XRoadJWSSigner(String keyId) {
        super(SUPPORTED_ALGORITHMS);
        this.keyId = keyId;
    }

    @Override
    public Base64URL sign(JWSHeader jwsHeader, byte[] bytes) throws JOSEException {
        try {
            SignMechanism signMechanismName = SignerProxy.getSignMechanism(keyId);
            DigestAlgorithm digestAlgorithm = getHashAlgorithm(jwsHeader.getAlgorithm());
            SignAlgorithm signatureAlgorithm = SignAlgorithm.ofDigestAndMechanism(digestAlgorithm, signMechanismName);

            byte[] digest = Digests.calculateDigest(digestAlgorithm, bytes);
            byte[] signature = SignerProxy.sign(keyId, signatureAlgorithm, digest);
            return Base64URL.encode(signature);
        } catch (Exception e) {
            throw new JOSEException(e);
        }
    }

    private DigestAlgorithm getHashAlgorithm(JWSAlgorithm jwsAlgorithm) throws NoSuchAlgorithmException {
        var hashAlgorithm = JWS_TO_HASH_ALGORITHM_MAP.get(jwsAlgorithm);
        if (hashAlgorithm == null) {
            throw new NoSuchAlgorithmException("Unsupported JWS algorithm: " + jwsAlgorithm);
        }
        return hashAlgorithm;
    }
}
