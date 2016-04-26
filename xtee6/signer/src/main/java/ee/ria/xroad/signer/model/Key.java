/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.signer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;

import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

/**
 * Model object representing a key.
 */
@Data
public final class Key {

    /** Reference to the token this key belongs to. */
    private final Token token;

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

    /**
     * Adds a certificate to this key.
     * @param cert the certificate to add
     */
    public void addCert(Cert cert) {
        certs.add(cert);
    }

    /**
     * Adds a certificate request to this key.
     * @param certReq the certificate request to add
     */
    public void addCertRequest(CertRequest certReq) {
        certRequests.add(certReq);
    }

    /**
     * Converts this object to value object.
     * @return the value object
     */
    public KeyInfo toDTO() {
        return new KeyInfo(available, usage, friendlyName, id, publicKey,
                Collections.unmodifiableList(getCertsAsDTOs()),
                Collections.unmodifiableList(getCertRequestsAsDTOs()));
    }

    private List<CertificateInfo> getCertsAsDTOs() {
        return certs.stream().map(c -> c.toDTO()).collect(Collectors.toList());
    }

    private List<CertRequestInfo> getCertRequestsAsDTOs() {
        return certRequests.stream().map(c -> c.toDTO())
                .collect(Collectors.toList());
    }
}
