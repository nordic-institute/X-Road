package ee.cyber.sdsb.signer.model;

import lombok.Value;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.protocol.dto.CertRequestInfo;

@Value
public class CertRequest {

    private final String id;

    private final ClientId memberId;

    private final String subjectName;

    public CertRequestInfo toDTO() {
        return new CertRequestInfo(id, memberId, subjectName);
    }
}
