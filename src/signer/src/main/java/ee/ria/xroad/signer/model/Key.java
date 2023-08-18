/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.signer.protocol.dto.CertRequestInfoProto;
import ee.ria.xroad.signer.protocol.dto.CertificateInfoProto;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfoProto;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableList;

/**
 * Model object representing a key.
 */
@Data
// a quick solution to avoid stack overflow if Token.toString() used, both key and token have references to each other.
@ToString(exclude = {"token"})
public final class Key {

    /**
     * Reference to the token this key belongs to.
     */
    private final Token token;

    /**
     * The unique key id.
     */
    private final String id;

    /**
     * Whether of not this key is available.
     */
    private boolean available;

    /**
     * Key usage info.
     */
    private KeyUsageInfo usage;

    /**
     * The friendly name of the key.
     */
    private String friendlyName;

    /**
     * The label of the key.
     */
    private String label;

    /**
     * The X509 encoded public key.
     */
    private String publicKey;

    /**
     * List of certificates.
     */
    private final List<Cert> certs = new ArrayList<>();

    /**
     * List of certificate requests.
     */
    private final List<CertRequest> certRequests = new ArrayList<>();

    /**
     * Adds a certificate to this key.
     *
     * @param cert the certificate to add
     */
    public void addCert(Cert cert) {
        certs.add(cert);
    }

    /**
     * Adds a certificate request to this key.
     *
     * @param certReq the certificate request to add
     */
    public void addCertRequest(CertRequest certReq) {
        certRequests.add(certReq);
    }

    public KeyInfoProto toProtoDTO() {
        var builder = KeyInfoProto.newBuilder()
                .setId(id)
                .setAvailable(available)
                .addAllCerts(unmodifiableList(getCertsAsDTOs()))
                .addAllCertRequests(unmodifiableList(getCertRequestsAsDTOs()))
                .setSignMechanismName(token.getSignMechanismName());

        if (usage != null) {
            builder.setUsage(usage);
        }

        if (friendlyName != null) {
            builder.setFriendlyName(friendlyName);
        }

        if (label != null) {
            builder.setLabel(label);
        }

        if (publicKey != null) {
            builder.setPublicKey(publicKey);
        }

        return builder.build();
    }

    /**
     * Converts this object to value object.
     *
     * @return the value object
     */
    public KeyInfo toDTO() {
        return new KeyInfo(toProtoDTO());
    }

    /**
     * @return true if the key is available and its usage info is signing
     */
    public boolean isValidForSigning() {
        return isAvailable() && getUsage() == KeyUsageInfo.SIGNING;
    }

    private List<CertificateInfoProto> getCertsAsDTOs() {
        return certs.stream().map(Cert::toProtoDTO).collect(Collectors.toList());
    }

    private List<CertRequestInfoProto> getCertRequestsAsDTOs() {
        return certRequests.stream().map(CertRequest::toProtoDTO).collect(Collectors.toList());
    }

}
