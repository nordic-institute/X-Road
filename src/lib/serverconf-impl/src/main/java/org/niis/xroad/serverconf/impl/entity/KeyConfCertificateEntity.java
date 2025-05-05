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
import jakarta.persistence.CascadeType;
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
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.time.Instant;

import static jakarta.persistence.AccessType.FIELD;

/**
 * keyconf_certificates table entity
 */
@Getter
@Setter
@Entity
@Table(name = KeyConfCertificateEntity.TABLE_NAME)
@Access(FIELD)
public class KeyConfCertificateEntity {

    public static final String TABLE_NAME = "keyconf_certificates";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "cert_id", unique = true)
    private String certId;

    @Column(name = "key_id", nullable = false)
    private Long keyId;

    @ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "member_id")
    private ClientIdEntity memberId;

    @Column(name = "contents", nullable = false)
    private byte[] contents;

    @Column(name = "status")
    private String status;

    @Column(name = "next_renewal_time")
    private Instant nextRenewalTime;

    @Column(name = "renewed_cert_hash")
    private String renewedCertHash;

    @Column(name = "renewal_error")
    private String renewalError;

    @Column(name = "ocsp_verify_error")
    private String ocspVerifyError;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        KeyConfCertificateEntity that = (KeyConfCertificateEntity) o;

        return new EqualsBuilder()
                .append(certId, that.certId)
                .append(keyId, that.keyId)
                .append(memberId, that.memberId)
                .append(contents, that.contents)
                .append(status, that.status)
                .append(nextRenewalTime, that.nextRenewalTime)
                .append(renewedCertHash, that.renewedCertHash)
                .append(renewalError, that.renewalError)
                .append(ocspVerifyError, that.ocspVerifyError)
                .append(active, that.active)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(certId)
                .append(keyId)
                .append(memberId)
                .append(contents)
                .append(status)
                .append(nextRenewalTime)
                .append(renewedCertHash)
                .append(renewalError)
                .append(ocspVerifyError)
                .append(active)
                .toHashCode();
    }
}
