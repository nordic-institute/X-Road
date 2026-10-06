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
package org.niis.xroad.proxy.core.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyProvider;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenSigningKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link AgreementTokenKeyProvider} backed entirely by an in-memory map, for tests that need real
 * mint/verify round trips without a vault.
 */
public final class InMemoryAgreementTokenKeyProvider implements AgreementTokenKeyProvider {

    private final Map<String, AgreementTokenSigningKey> keysById = new LinkedHashMap<>();
    private String activeKeyId;

    public static InMemoryAgreementTokenKeyProvider withGeneratedKey(String keyId) {
        var provider = new InMemoryAgreementTokenKeyProvider();
        provider.addKey(keyId, generateKeyPair());
        return provider;
    }

    public void addKey(String keyId, ECKey keyPair) {
        keysById.put(keyId, new AgreementTokenSigningKey(keyId, keyPair));
        activeKeyId = keyId;
    }

    @Override
    public AgreementTokenSigningKey activeKey() {
        return keysById.get(activeKeyId);
    }

    @Override
    public Optional<AgreementTokenSigningKey> keyById(String keyId) {
        return Optional.ofNullable(keysById.get(keyId));
    }

    @Override
    public AgreementTokenSigningKey rotate() {
        throw new UnsupportedOperationException("not needed by this test");
    }

    @Override
    public void refresh() {
    }

    private static ECKey generateKeyPair() {
        try {
            return new ECKeyGenerator(Curve.P_256).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
