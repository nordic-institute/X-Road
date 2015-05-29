package ee.ria.xroad_legacy.proxy.securelog;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;

import static ee.ria.xroad.common.util.CryptoUtils.*;

/**
 * Log record for SOAP messages.
 *
 * The explicit class is needed mostly just to have objects that are both
 * PrevLogRecord and TodoRecord at the same time.
 */
class SoapRecord extends LogRecord {
    private SignatureRecord sigRecord;

    SoapRecord(SoapMessageImpl message, SignatureRecord sigRecord,
            SignatureData signatureData) throws Exception {
        super(Type.SOAP, formatQueryId(message.getQueryId()),
                encodeBase64(message.getBytes()),
                null/* signature */,
                signatureData.getHashChainResult() != null
                    ? encodeBase64(signatureData.getHashChainResult())
                    : MISSING_VALUE,
                signatureData.getHashChain() != null
                    ? encodeBase64(signatureData.getHashChain())
                    : MISSING_VALUE);
        this.sigRecord = sigRecord;
    }

    private static String formatQueryId(String queryId) throws Exception {
        return hexDigest(MD5_ID, queryId);
    }

    @Override
    void calculateFields(PrevRecord previous, String hashAlg) throws Exception {
        super.calculateFields(previous, hashAlg);
        fields[7] = String.valueOf(sigRecord.getNr());
    }
}
