package ee.cyber.sdsb.common.hashchain;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERUTF8String;

import static ee.cyber.sdsb.common.util.CryptoUtils.calculateDigest;
import static ee.cyber.sdsb.common.util.CryptoUtils.getDigestAlgorithmURI;
import static org.bouncycastle.asn1.ASN1Encoding.DER;

class DigestList {
    /**
     * Takes as input a sequence of hashes, combines them using DigestList
     * data structure and computes hash of the data structure.
     */
    static byte[] digestHashStep(String digestMethod, byte[] ...items)
            throws Exception {
        return calculateDigest(digestMethod,
                concatDigests(getDigestAlgorithmURI(digestMethod), items));
    }

    /**
     * Takes as input a sequence of hashes and combines them using DigestList
     * data structure.
     */
    static byte[] concatDigests(String digestMethodUri, byte[] ...items)
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
    static byte[] concatDigests(DigestValue ...items) throws Exception {
        ASN1Encodable[] digestList = new ASN1Encodable[items.length];

        for (int i = 0; i < items.length; ++i) {
            digestList[i] = singleDigest(items[i].getDigestMethod(),
                    items[i].getDigestValue());
        }

        DERSequence step = new DERSequence(digestList);
        return step.getEncoded(DER);
    }

    /**
     * Encodes hash value as SingleDigest data structure.
     */
    private static DERSequence singleDigest(String digestMethodUri,
            byte[] digest) throws Exception {
        DEROctetString digestValue = new DEROctetString(digest);
        DERUTF8String digestMethod = new DERUTF8String(digestMethodUri);

        DERSequence transforms = new DERSequence();

        return new DERSequence(new ASN1Encodable[] {
                digestValue, digestMethod, transforms });
    }
}
