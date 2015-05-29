package ee.ria.xroad.signer.protocol.dto;

import java.io.Serializable;

import lombok.Value;

import ee.ria.xroad.common.identifier.ClientId;

/**
 * Certificate request info DTO.
 */
@Value
public class CertRequestInfo implements Serializable {

    private final String id;

    private final ClientId memberId;

    private final String subjectName;

}
