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
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapMessageImpl;
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

    @Getter
    @Setter
    private String memberClass;

    @Getter
    @Setter
    private String memberCode;

    @Getter
    @Setter
    private String subsystemCode;

    /**
     * Constructs a message record.
     * @param msg the message
     * @param sig the signature
     * @param clientId message sender client identifier
     * @throws Exception in case of any errors
     */
    public MessageRecord(SoapMessageImpl msg, String sig, ClientId clientId)
            throws Exception {
        this(msg.getQueryId(), msg.getXml(), sig, msg.isResponse(), clientId);
    }

    /**
     * Constructs a message record.
     * @param qid the query ID
     * @param msg the message
     * @param sig the signature
     * @param response whether this record is for a response
     * @param clientId message sender client identifier
     */
    public MessageRecord(String qid, String msg, String sig, boolean response,
            ClientId clientId) {
        this.queryId = qid;
        this.message = msg;
        this.signature = sig;
        this.response = response;
        this.memberClass = clientId.getMemberClass();
        this.memberCode = clientId.getMemberCode();
        this.subsystemCode = clientId.getSubsystemCode();
    }

    @Override
    public Object[] getLinkingInfoFields() {
        return new Object[] {getId(), getTime(), queryId, message, signature,
                memberClass, memberCode, subsystemCode};
    }

    /**
     * @return an ASiC container constructed from this message record
     * @throws Exception in case of any errors
     */
    public AsicContainer toAsicContainer() throws Exception {
        log.trace("toAsicContainer({})", queryId);

        SignatureData signatureData =
                new SignatureData(signature, hashChainResult, hashChain);

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
