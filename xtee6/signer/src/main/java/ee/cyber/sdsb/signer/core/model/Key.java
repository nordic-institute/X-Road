package ee.cyber.sdsb.signer.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Data;

import ee.cyber.sdsb.signer.protocol.dto.CertRequestInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;

@Data
public final class Key {

    /** Reference to the device this key belongs to. */
    private final Token device;

    /** The unique key id. */
    private final String id;

    /** Whether of not this key is available. */
    private boolean available;

    /** Key usage info. */
    private KeyUsageInfo usage;

    /** The friendly name of the key. */
    private String friendlyName;

    /** The X509 encoded public key. */
    private String publicKey;

    /** List of certificates. */
    private final List<Cert> certs = new ArrayList<>();

    /** List of certificate requests. */
    private final List<CertRequest> certRequests = new ArrayList<>();

    public void addCert(Cert cert) {
        certs.add(cert);
    }

    public void addCertRequest(CertRequest certReq) {
        certRequests.add(certReq);
    }

    public KeyInfo toDTO() {
        return new KeyInfo(available, usage, friendlyName, id, publicKey,
                Collections.unmodifiableList(getCertsAsDTOs()),
                Collections.unmodifiableList(getCertRequestsAsDTOs()));
    }

    private List<CertificateInfo> getCertsAsDTOs() {
        List<CertificateInfo> certInfos = new ArrayList<>();
        for (Cert cert : certs) {
            certInfos.add(cert.toDTO());
        }

        return certInfos;
    }

    private List<CertRequestInfo> getCertRequestsAsDTOs() {
        List<CertRequestInfo> certReqInfos = new ArrayList<>();
        for (CertRequest certReq : certRequests) {
            certReqInfos.add(certReq.toDTO());
        }

        return certReqInfos;
    }
}
