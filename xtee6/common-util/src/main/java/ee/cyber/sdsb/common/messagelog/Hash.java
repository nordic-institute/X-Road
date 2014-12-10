package ee.cyber.sdsb.common.messagelog;

import lombok.Data;

import org.apache.commons.lang.StringUtils;

@Data
public final class Hash {

    private static final char SEPARATOR = ':';

    private final String algoId;
    private final String hashValue;

    public Hash(String hashString) {
        if (hashString == null) {
            throw new IllegalArgumentException("hashString must not be null");
        }

        int idx = hashString.indexOf(SEPARATOR);
        if (idx == -1) {
            throw new IllegalArgumentException("hash string must be in the "
                    + "form of '<algorithm id>" + SEPARATOR + "<value>'");
        }

        algoId = hashString.substring(0, idx);
        hashValue = hashString.substring(idx + 1);

        verifyFields();
    }

    public Hash(String algoId, String hashValue) {
        this.algoId = algoId;
        this.hashValue = hashValue;

        verifyFields();
    }

    @Override
    public String toString() {
        return algoId + SEPARATOR + hashValue;
    }

    private void verifyFields() {
        if (StringUtils.isBlank(algoId)) {
            throw new IllegalArgumentException("algoId must not be blank");
        }

        if (StringUtils.isBlank(hashValue)) {
            throw new IllegalArgumentException("hashValue must not be blank");
        }
    }
}
