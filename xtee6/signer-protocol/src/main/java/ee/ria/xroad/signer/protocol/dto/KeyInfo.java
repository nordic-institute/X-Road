package ee.ria.xroad.signer.protocol.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Value;

/**
 * Tiny container class to help handle the key list
 */
@Value
public final class KeyInfo implements Serializable {

    private final boolean available;

    private final KeyUsageInfo usage;

    private final String friendlyName;

    private final String id;

    private final String label;
    
    private final String publicKey;

    private final List<CertificateInfo> certs;

    private final List<CertRequestInfo> certRequests;

}
