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
package ee.ria.xroad.common.hashchain;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERUTF8String;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static org.bouncycastle.asn1.ASN1Encoding.DER;

final class DigestList {

    private DigestList() {
    }

    /**
     * Takes as input a sequence of hashes, combines them using DigestList
     * data structure and computes hash of the data structure.
     */
    static byte[] digestHashStep(DigestAlgorithm digestMethod, byte[]... items)
            throws Exception {
        return calculateDigest(digestMethod,
                concatDigests(digestMethod, items));
    }

    /**
     * Takes as input a sequence of hashes and combines them using DigestList
     * data structure.
     */
    static byte[] concatDigests(DigestAlgorithm digestMethodUri, byte[]... items)
            throws Exception {
        ASN1Encodable[] digestList = new ASN1Encodable[items.length];

        for (int i = 0; i < items.length; ++i) {
            digestList[i] = singleDigest(digestMethodUri, items[i]);
        }

        DERSequence step = new DERSequence(digestList);
        return step.getEncoded(DER);
    }

    /**
     * Takes as input a sequence of hashes and combines them using DigestList
     * data structure.
     */
    static byte[] concatDigests(DigestValue... items) throws Exception {
        ASN1Encodable[] digestList = new ASN1Encodable[items.length];

        for (int i = 0; i < items.length; ++i) {
            digestList[i] = singleDigest(items[i].digestMethod(),
                    items[i].digestValue());
        }

        DERSequence step = new DERSequence(digestList);
        return step.getEncoded(DER);
    }

    /**
     * Encodes hash value as SingleDigest data structure.
     */
    private static DERSequence singleDigest(DigestAlgorithm digestMethodUri,
                                            byte[] digest) {
        DEROctetString digestValue = new DEROctetString(digest);
        DERUTF8String digestMethod = new DERUTF8String(digestMethodUri.uri());

        DERSequence transforms = new DERSequence();

        return new DERSequence(new ASN1Encodable[]{
                digestValue, digestMethod, transforms});
    }
}
