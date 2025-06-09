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
package org.niis.xroad.serverconf.impl.entity;

import jakarta.persistence.Access;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import static jakarta.persistence.AccessType.FIELD;

@Getter
@Setter
@Entity
@Access(FIELD)
@Table(name = SignerCertificateEntity.TABLE_NAME)
public class SignerCertificateEntity {

    public static final String TABLE_NAME = "signer_certificates";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "external_id", unique = true)
    private String externalId;

    @Column(name = "key_id", nullable = false)
    private Long keyId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id")
    private ClientIdEntity member;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "status")
    private String status;

    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "next_renewal_time")
    private Instant nextRenewalTime;

    @Column(name = "renewed_cert_hash")
    private String renewedCertHash;

    @Column(name = "renewal_error")
    private String renewalError;

    @Column(name = "ocsp_verify_error")
    private String ocspVerifyError;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SignerCertificateEntity that = (SignerCertificateEntity) o;
        return Objects.equals(id, that.id)
                && Objects.equals(externalId, that.externalId)
                && Objects.equals(keyId, that.keyId)
                && Objects.equals(member, that.member)
                && Objects.equals(active, that.active)
                && Objects.equals(status, that.status)
                && Objects.deepEquals(data, that.data)
                && Objects.equals(nextRenewalTime, that.nextRenewalTime)
                && Objects.equals(renewedCertHash, that.renewedCertHash)
                && Objects.equals(renewalError, that.renewalError)
                && Objects.equals(ocspVerifyError, that.ocspVerifyError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, externalId, keyId, member, active, status, Arrays.hashCode(data),
                nextRenewalTime, renewedCertHash, renewalError, ocspVerifyError);
    }
}
