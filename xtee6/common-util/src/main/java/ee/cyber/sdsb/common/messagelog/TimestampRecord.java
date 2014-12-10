package ee.cyber.sdsb.common.messagelog;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TimestampRecord extends AbstractLogRecord {

    private String ts; // base-64 encoded time-stamp DER
    private String hcr; // time-stamp hash chain result

    public String getTimestamp() {
        return ts;
    }

    public void setTimestamp(String ts) {
        this.ts = ts;
    }

    public String getHashChainResult() {
        return hcr;
    }

    public void setHashChainResult(String hcr) {
        this.hcr = hcr;
    }

    @Override
    public Object[] getLinkingInfoFields() {
        return new Object[] { getNumber(), getTime(), ts };
    }
}
