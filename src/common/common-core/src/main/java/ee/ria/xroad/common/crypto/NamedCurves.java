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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.experimental.UtilityClass;
import org.apache.xml.security.algorithms.implementations.ECDSAUtils;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;

import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidParameterSpecException;
import java.util.concurrent.ExecutionException;

import static ee.ria.xroad.common.util.EncoderUtils.encodeHex;

@UtilityClass
public class NamedCurves {
    private static final int MAXIMUM_CACHE_SIZE = 50;
    private static final Cache<EllipticCurve, String> CURVE_NAMES_CACHE = CacheBuilder.newBuilder()
            .maximumSize(MAXIMUM_CACHE_SIZE)
            .build();

    public static byte[] getOIDAsBytes(String namedCurve) {
        var ident = ECNamedCurveTable.getOID(namedCurve);
        if (ident == null) {
            throw new CryptoException("Unknown named curve: " + namedCurve);
        }
        try {
            return ident.getEncoded();
        } catch (IOException e) {
            throw new CryptoException("Failed to get OID bytes for " + namedCurve, e);
        }
    }

    public static String getCurveName(ECPublicKey ecPublicKey) {
        try {
            return CURVE_NAMES_CACHE.get(ecPublicKey.getParams().getCurve(), () -> internalGetCurveName(ecPublicKey));
        } catch (ExecutionException e) {
            throw new CryptoException("Failed to get curve name", e);
        }
    }

    public static String getEncodedPoint(ECPublicKey ecPublicKey) {
        return encodeHex(ECDSAUtils.encodePoint(ecPublicKey.getW(), ecPublicKey.getParams().getCurve()));
    }


    private static String internalGetCurveName(ECPublicKey ecPublicKey) {
        try {
            AlgorithmParameters algoParams = AlgorithmParameters.getInstance("EC");
            algoParams.init(ecPublicKey.getParams());
            return algoParams.toString();
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
            throw new CryptoException("Failed to resolve curve name", e);
        }
    }
}
