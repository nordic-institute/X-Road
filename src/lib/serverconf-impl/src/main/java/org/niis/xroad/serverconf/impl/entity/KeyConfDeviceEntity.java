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

import java.util.LinkedHashSet;
import java.util.Set;

import static jakarta.persistence.AccessType.FIELD;

/**
 * keyconf_devices table entity
 */
@Getter
@Setter
@Entity
@Table(name = KeyConfDeviceEntity.TABLE_NAME)
@Access(FIELD)
public class KeyConfDeviceEntity {

    public static final String TABLE_NAME = "keyconf_devices";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Column(name = "device_type", nullable = false)
    private String deviceType;

    @Column(name = "friendly_name")
    private String friendlyName;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "slot_id")
    private String slotId;

    @Column(name = "pin_index")
    private Integer pinIndex = 1;

    @Column(name = "sign_mechanism_name")
    private SignMechanism signMechanismName;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<KeyConfKeyEntity> keys = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        KeyConfDeviceEntity that = (KeyConfDeviceEntity) o;

        return new EqualsBuilder()
                .append(deviceId, that.deviceId)
                .append(deviceType, that.deviceType)
                .append(friendlyName, that.friendlyName)
                .append(tokenId, that.tokenId)
                .append(slotId, that.slotId)
                .append(pinIndex, that.pinIndex)
                .append(signMechanismName, that.signMechanismName)
                .append(keys, that.keys)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(deviceId)
                .append(deviceType)
                .append(friendlyName)
                .append(tokenId)
                .append(slotId)
                .append(pinIndex)
                .append(signMechanismName)
                .append(keys)
                .toHashCode();
    }
}
