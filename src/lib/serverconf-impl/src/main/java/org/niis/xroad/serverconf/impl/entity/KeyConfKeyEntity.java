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

import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import jakarta.persistence.Access;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.niis.xroad.serverconf.impl.entity.type.KeyUsage;

import java.util.LinkedHashSet;
import java.util.Set;

import static jakarta.persistence.AccessType.FIELD;

/**
 * keyconf_keys table entity
 */
@Getter
@Setter
@Entity
@Table(name = KeyConfKeyEntity.TABLE_NAME)
@Access(FIELD)
public class KeyConfKeyEntity {

    public static final String TABLE_NAME = "keyconf_keys";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "key_id", nullable = false, unique = true)
    private String keyId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "friendly_name", nullable = false)
    private String friendlyName;

    @Column(name = "label")
    private String label;

    @Column(name = "public_key")
    private String publicKey;

    @Column(name = "private_key")
    private byte[] privateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "sign_mechanism_name")
    private SignMechanism signMechanismName;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage")
    private KeyUsage usage;

    @OneToMany(mappedBy = "key", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private Set<KeyConfCertificateEntity> certificates = new LinkedHashSet<>();

    @OneToMany(mappedBy = "key", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private Set<KeyConfCertRequestEntity> certRequests = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        KeyConfKeyEntity that = (KeyConfKeyEntity) o;

        return new EqualsBuilder()
                .append(keyId, that.keyId)
                .append(deviceId, that.deviceId)
                .append(friendlyName, that.friendlyName)
                .append(label, that.label)
                .append(publicKey, that.publicKey)
                .append(privateKey, that.privateKey)
                .append(signMechanismName, that.signMechanismName)
                .append(usage, that.usage)
                .append(certificates, that.certificates)
                .append(certRequests, that.certRequests)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(keyId)
                .append(deviceId)
                .append(friendlyName)
                .append(label)
                .append(publicKey)
                .append(privateKey)
                .append(signMechanismName)
                .append(usage)
                .append(certificates)
                .append(certRequests)
                .toHashCode();
    }
}
