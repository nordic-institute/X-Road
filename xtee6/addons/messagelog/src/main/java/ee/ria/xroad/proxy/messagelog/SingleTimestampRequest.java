package ee.ria.xroad.proxy.messagelog;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.tsp.TimeStampResponse;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.signature.Signature;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Creates a timestamp request for a single message.
 *
 * The data to be time-stamped is the ds:SignatureValue element.
 */
@Slf4j
class SingleTimestampRequest extends AbstractTimestampRequest {

    private final LogRecordManager logRecordManager = new LogRecordManager();

    private MessageRecord message;
    private Signature signature;

    SingleTimestampRequest(Long logRecord) {
        super(new Long[] {logRecord});
    }

    @Override
    byte[] getRequestData() throws Exception {
        LogRecord record = logRecordManager.get(logRecords[0]);
        if (record == null || !(record instanceof MessageRecord)) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not find message record #" + logRecords[0]);
        }

        message = (MessageRecord) record;

        signature = new Signature(message.getSignature());

        return signature.getXmlSignature().getSignatureValue();
    }

    @Override
    Object result(TimeStampResponse tsResponse) throws Exception {
        byte[] timestampDer = getTimestampDer(tsResponse);

        updateSignatureProperties(timestampDer);

        return new Timestamper.TimestampSucceeded(logRecords, timestampDer,
                null, null);
    }

    private void updateSignatureProperties(byte[] timestampDer)
            throws Exception {
        log.trace("Updating unsigned signature properties");

        signature.addSignatureTimestamp(timestampDer);

        String signatureXml = signature.toXml();

        message.setSignature(signatureXml);
        message.setSignatureHash(LogManager.signatureHash(signatureXml));

        logRecordManager.updateMessageRecord(message);
    }
}
