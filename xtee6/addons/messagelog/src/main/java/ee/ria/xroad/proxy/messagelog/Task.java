package ee.ria.xroad.proxy.messagelog;

import lombok.Data;

@Data
class Task {

    private final Long messageRecordNo;
    private final String signatureHash;

}
