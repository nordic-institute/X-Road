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

package ee.ria.xroad.common.crypto;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import java.security.NoSuchAlgorithmException;
import java.util.Map;

public final class KeyManagers {
    private final Map<KeyAlgorithm, KeyManager> byType;

    public KeyManagers(int rsaKeyLength, String keyNamedCurve) {
        byType = Map.of(
                KeyAlgorithm.RSA, new RsaKeyManager(rsaKeyLength),
                KeyAlgorithm.EC, new EcKeyManager(keyNamedCurve)
        );
    }

    public KeyManager getFor(String keyAlgorithm) {
        return getFor(KeyAlgorithm.valueOf(keyAlgorithm));
    }

    public KeyManager getFor(KeyAlgorithm keyAlgorithm) {
        return byType.get(keyAlgorithm);
    }

    public KeyManager getFor(SignMechanism mechanism) throws NoSuchAlgorithmException {
        return getFor(mechanism.keyAlgorithm());
    }

    public KeyManager getFor(SignAlgorithm algorithmName) throws NoSuchAlgorithmException {
        return getFor(algorithmName.algorithm());
    }

    public RsaKeyManager getForRSA() {
        return (RsaKeyManager) getFor(KeyAlgorithm.RSA);
    }

    public EcKeyManager getForEC() {
        return (EcKeyManager) getFor(KeyAlgorithm.EC);
    }

}
