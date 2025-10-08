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
package org.niis.xroad.signer.core.model;

import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;

/**
 * Model object representing a key.
 */
@Setter
public final class RuntimeKeyImpl implements RuntimeKey {

    private BasicKeyInfo data;

    /**
     * List of certificates.
     */
    private final List<RuntimeCertImpl> certs = new ArrayList<>();

    /**
     * List of certificate requests.
     */
    private final List<CertRequestData> certRequests = new ArrayList<>();

    /**
     * Whether of not this key is available.
     */
    @Getter
    private boolean available;

    @Override
    public Long id() {
        return data.id();
    }

    @Override
    public Long tokenId() {
        return data.tokenId();
    }

    @Override
    public String externalId() {
        return data.externalId();
    }

    @Override
    public KeyUsageInfo usage() {
        return data.usage();
    }

    @Override
    public String friendlyName() {
        return data.friendlyName();
    }

    @Override
    public String label() {
        return data.label();
    }

    @Override
    public String publicKey() {
        return data.publicKey();
    }

    @Override
    public SignMechanism signMechanismName() {
        return data.signMechanismName();
    }

    @Override
    public Optional<byte[]> softwareKeyStore() {
        return data.softwareKeyStore();
    }

    @Override
    public Collection<RuntimeCert> certs() {
        return Collections.unmodifiableList(certs);
    }

    @Override
    public Collection<CertRequestData> certRequests() {
        return Collections.unmodifiableList(certRequests);
    }

    @Override
    public boolean isValidForSigning() {
        return isAvailable() && usage() == KeyUsageInfo.SIGNING;
    }

    public void addCertRequest(CertRequestData certRequest) {
        if (certRequest == null) {
            throw new IllegalArgumentException("Cert request cannot be null");
        }
        certRequests.add(certRequest);
    }

    public void addTransientCert(String certId, byte[] certBytes) {
        try {
            var x509Certificate = CryptoUtils.readCertificate(certBytes);
            var certHash = calculateCertHexHash(x509Certificate);

            var cert = CertData.create(certId, data.id(), x509Certificate, certHash);
            var runtimeCert = new RuntimeCertImpl();
            runtimeCert.setData(cert);
            runtimeCert.setTransientCert(true);

            certs.add(runtimeCert);
        } catch (Exception e) {
            throw XrdRuntimeException.systemInternalError("Failed to add transient certificate", e);
        }
    }

    public void addCert(RuntimeCertImpl cert) {
        if (cert == null) {
            throw new IllegalArgumentException("Certificate cannot be null");
        }
        certs.add(cert);
    }

    public void transferTransientData(RuntimeKey other) {
        if (other == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        this.available = other.isAvailable();

        other.certs().stream()
                .filter(RuntimeCert::isTransientCert)
                .map(RuntimeCertImpl.class::cast)
                .forEach(certs::add);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RuntimeKeyImpl.class.getSimpleName() + "[", "]")
                .add("data=" + data)
                .add("available=" + available)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RuntimeKeyImpl that = (RuntimeKeyImpl) o;
        return available == that.available
                && Objects.equals(data, that.data)
                && Objects.equals(certs, that.certs)
                && Objects.equals(certRequests, that.certRequests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, certs, certRequests, available);
    }
}
