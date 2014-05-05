package ee.cyber.sdsb.proxy.securelog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.signature.Signature;
import ee.cyber.sdsb.common.signature.SignatureData;

/**
 * Saves messages to secure log.
 */
public class SecureLog {
    private static final Logger LOG = LoggerFactory.getLogger(SecureLog.class);

    /**
     * Save the message and signature to secure log. Attachments are not logged.
     *
     * @throws Exception
     */
    public static void logSignature(SoapMessageImpl message,
            Signature signature) throws Exception {
        logSignature(message, new SignatureData(signature.toXml(), null, null));
    }

    /**
     * Save the message and signature to secure log. Attachments are not logged.
     *
     * @throws Exception
     */
    public static void logSignature(SoapMessageImpl message,
            SignatureData signature) throws Exception {
        LOG.trace("logSignature()");

        // TODO: remove the manipulation of Signature objects from
        // SecureLog. Can be done with hashchain-based batch timestamps.
        SignatureRecord sigRecord = SignatureRecord.create(
                new Signature(signature.getSignatureXml()));
        LogManager.log(sigRecord);

        SoapRecord soapRecord = new SoapRecord(message, sigRecord, signature);
        LogManager.log(soapRecord);

        sigRecord.waitUntilDone();
        soapRecord.waitUntilDone();
    }
}
