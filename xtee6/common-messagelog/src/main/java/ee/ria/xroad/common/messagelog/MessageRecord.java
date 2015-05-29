package ee.ria.xroad.common.messagelog;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.asic.AsicContainer;
import ee.ria.xroad.common.asic.TimestampData;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CryptoUtils;

import static ee.ria.xroad.common.util.CryptoUtils.MD5_ID;
import static ee.ria.xroad.common.util.CryptoUtils.hexDigest;

/**
 * A message log record.
 */
@Slf4j
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class MessageRecord extends AbstractLogRecord {

    @Getter
    @Setter
    private String queryId;

    @Getter
    @Setter
    private String message;

    @Getter
    @Setter
    private String signature;

    @Getter
    @Setter
    private String hashChain;

    @Getter
    @Setter
    private String hashChainResult;

    @Getter
    @Setter
    private String signatureHash;

    @Getter
    @Setter
    private TimestampRecord timestampRecord;

    @Getter
    @Setter
    private String timestampHashChain;

    @Getter
    @Setter
    private boolean response;

    /**
     * Constructs a message record.
     * @param qid the query ID
     * @param msg the message
     * @param sig the signature
     * @param response whether this record is for a response
     */
    public MessageRecord(String qid, String msg, String sig, boolean response) {
        this.queryId = qid;
        this.message = msg;
        this.signature = sig;
        this.response = response;
    }

    @Override
    public Object[] getLinkingInfoFields() {
        return new Object[] {getId(), getTime(), queryId, message, signature};
    }

    /**
     * @return an ASiC container constructed from this message record
     * @throws Exception in case of any errors
     */
    public AsicContainer toAsicContainer() throws Exception {
        log.trace("toAsicContainer()", queryId);

        traceHashChainData();

        SignatureData signatureData = new SignatureData(
                this.signature,
                hashChainResult,
                hashChain);
        TimestampData timestamp = null;

        if (timestampRecord != null) {
            timestamp = new TimestampData(
                    timestampRecord.getTimestamp(),
                    timestampRecord.getHashChainResult(),
                    timestampHashChain);
        }

        return new AsicContainer(message, signatureData, timestamp);
    }

    /**
     * @return a hex digest of the given query ID
     * @throws Exception if any errors occur
     */
    private void traceHashChainData() {
        log.trace("Hash chain:\n{}", hashChain);
        log.trace("Hash chain result:\n{}", hashChainResult);
        log.trace("Timestamp hash chain:\n{}", timestampHashChain);
        log.trace("Timestamp hash chain result:\n{}",
                timestampRecord.getHashChainResult());
    }

    /**
     * @param queryId the query ID
     * @return MD5 hex digest of the given query ID
     * @throws Exception if any errors occur
     */
    public static String hashQueryId(String queryId) throws Exception {
        return hexDigest(MD5_ID, queryId);
    }

    static String decodeBase64(String base64Encoded) {
        return (base64Encoded != null && !base64Encoded.isEmpty())
                ? new String(CryptoUtils.decodeBase64(base64Encoded)) : null;
    }
}
