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

import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class NamedCurves {
    private static final Map<String, byte[]> EC_CURVE_OID_CACHE = new HashMap<>();

    public static String getOID(String namedCurve) {
        var ident = ECNamedCurveTable.getOID(namedCurve);
        if (ident == null) {
            throw new UnknownAlgorithmException("Unknown named curve: " + namedCurve);
        }
        return ident.getId();

    }

    public static byte[] getOIDAsBytes(String namedCurve) {
        var ident = ECNamedCurveTable.getOID(namedCurve);
        if (ident == null) {
            throw new UnknownAlgorithmException("Unknown named curve: " + namedCurve);
        }
        try {
            return ident.getEncoded();
        } catch (IOException e) {
            throw new CryptoException("Failed to get OID bytes for " + namedCurve, e);
        }
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(NamedCurves.getOIDAsBytes("secp256r1")));
        System.out.println(NamedCurves.getOID("secp256r1"));
    }
}
