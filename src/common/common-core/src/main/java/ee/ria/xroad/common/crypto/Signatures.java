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

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.algorithms.implementations.ECDSAUtils;

import java.io.IOException;

@UtilityClass
@Slf4j
public class Signatures {
    private static final byte ASN1_DER_SEQUENCE_TAG = 0x30;

    public static boolean isAsn1DerSignature(byte[] signature) {
        return signature.length > 1 && signature[0] == ASN1_DER_SEQUENCE_TAG && signature[1] + 2 == signature.length;
    }

    /**
     * In case of ECDSA signatures, strip the ASN.1 DER encoding.
     * In case of RSA signatures, do nothing.
     * @param algorithm algorithm used to sign
     * @param signature to reformat
     * @return reformatted signature
     */
    public static byte[] useRawFormat(SignAlgorithm algorithm, byte[] signature) throws IOException {
        if (!KeyAlgorithm.EC.equals(algorithm.algorithm()) || !isAsn1DerSignature(signature)) {
            return signature;
        }

        return ECDSAUtils.convertASN1toXMLDSIG(signature, -1);
    }

    /**
     * In case of ECDSA signatures, add the ASN.1 DER encoding.
     * In case of RSA signatures, do nothing.
     * @param algorithm algorithm used to sign
     * @param signature to reformat
     * @return reformatted signature
     */
    public static byte[] useAsn1DerFormat(SignAlgorithm algorithm, byte[] signature) throws IOException {
        if (!KeyAlgorithm.EC.equals(algorithm.algorithm()) || isAsn1DerSignature(signature)) {
            return signature;
        }

        return ECDSAUtils.convertXMLDSIGtoASN1(signature);
    }
}
