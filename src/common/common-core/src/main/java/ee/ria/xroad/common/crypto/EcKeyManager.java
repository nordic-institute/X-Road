/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.crypto;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;

public final class EcKeyManager extends AbstractKeyManager {

    // Use no digesting algorithm, since the input data is already a digest
    private static final SignAlgorithm SIGNATURE_ALGORITHM = SignAlgorithm.ofName("NONEwithECDSA");
    private static final KeyAlgorithm CRYPTO_ALGORITHM = KeyAlgorithm.EC;

    EcKeyManager() {
        super(CRYPTO_ALGORITHM);
    }

    @Override
    public SignAlgorithm getSoftwareTokenSignAlgorithm() {
        return SIGNATURE_ALGORITHM;
    }

    @Override
    public SignAlgorithm getSoftwareTokenKeySignAlgorithm() {
        return SignAlgorithm.SHA512_WITH_ECDSA;
    }

    @Override
    public KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(cryptoAlgorithm().name(), BOUNCY_CASTLE);

        var spec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec(SystemProperties.getSignerKeyNamedCurve());
        if (spec == null) {
            var supported = StringUtils.join(org.bouncycastle.jce.ECNamedCurveTable.getNames().asIterator(), ", ");
            throw new CryptoException(
                    "Named curve not found: %s, please on of supported: %s"
                            .formatted(SystemProperties.getSignerKeyNamedCurve(), supported)
            );
        }

        keyPairGen.initialize(spec, new SecureRandom());

        return keyPairGen.generateKeyPair();
    }

    public byte[] generateX509PublicKey(byte[] ecCurveData, byte[] ecPointData)
            throws InvalidKeySpecException, IOException {

        if (!(ASN1Primitive.fromByteArray(ecCurveData) instanceof ASN1ObjectIdentifier oid)) {
            throw new CryptoException("Cannot read OID from provided bytes");
        }

        if (!(ASN1Primitive.fromByteArray(ecPointData) instanceof DEROctetString pointAsOctets)) {
            throw new CryptoException("Cannot read point data from provided bytes");
        }

        var params = ECNamedCurveTable.getByOID(oid);

        var spec = new ECNamedCurveParameterSpec(
                ECNamedCurveTable.getName(oid),
                params.getCurve(),
                params.getG(),
                params.getN(),
                params.getH(),
                params.getSeed()
        );

        var ecPoint = params.getCurve().decodePoint(pointAsOctets.getOctets());

        return generateX509PublicKey(new ECPublicKeySpec(ecPoint, spec));
    }

}
