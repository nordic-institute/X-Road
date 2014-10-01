package ee.cyber.sdsb.common.securelog;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import static ee.cyber.sdsb.common.util.CryptoUtils.MD5_ID;
import static ee.cyber.sdsb.common.util.CryptoUtils.hexDigest;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class MessageRecord extends AbstractLogRecord {

    private String qid; // query id
    private String msg; // message base64
    private String sig; // signature base64

    private String hc; // hash chain
    private String hcr; // hash chain result

    @Getter
    @Setter
    private String signatureHash; // hash of the signature to be time-stamped
    private TimestampRecord tsno; // time-stamp log record
    private String tshc; // time-stamp hash chain

    public MessageRecord(String qid, String msg, String sig) {
        setQueryId(qid);
        setMessage(msg);
        setSignature(sig);
    }

    public String getQueryId() {
        return qid;
    }

    public void setQueryId(String qid) {
        this.qid = qid;
    }

    public String getMessage() {
        return msg;
    }

    public void setMessage(String msg) {
        this.msg = msg;
    }

    public String getSignature() {
        return sig;
    }

    public void setSignature(String sig) {
        this.sig = sig;
    }

    public String getHashChainResult() {
        return hcr;
    }

    public void setHashChainResult(String hcr) {
        this.hcr = hcr;
    }

    public String getHashChain() {
        return hc;
    }

    public void setHashChain(String hc) {
        this.hc = hc;
    }

    public TimestampRecord getTimestampRecord() {
        return tsno;
    }

    public void setTimestampRecord(TimestampRecord tsno) {
        this.tsno = tsno;
    }

    public String getTimestampHashChain() {
        return tshc;
    }

    public void setTimestampHashChain(String tshc) {
        this.tshc = tshc;
    }

    @Override
    public Object[] getLinkingInfoFields() {
        return new Object[] { getNumber(), getTime(), qid, msg, sig };
    }

    public static String hashQueryId(String queryId) throws Exception {
        return hexDigest(MD5_ID, queryId);
    }
}
