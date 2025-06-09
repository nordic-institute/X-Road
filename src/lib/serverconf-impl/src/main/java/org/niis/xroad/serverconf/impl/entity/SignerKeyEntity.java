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
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.serverconf.impl.entity.type.KeyType;
import org.niis.xroad.serverconf.impl.entity.type.KeyUsage;

import java.util.Arrays;
import java.util.Objects;

import static jakarta.persistence.AccessType.FIELD;

@Getter
@Setter
@Entity
@Access(FIELD)
@Table(name = SignerKeyEntity.TABLE_NAME)
public class SignerKeyEntity {

    public static final String TABLE_NAME = "signer_keys";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "token_id", nullable = false)
    private Long tokenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private KeyType type;

    @Column(name = "friendly_name", nullable = false)
    private String friendlyName;

    @Column(name = "label")
    private String label;

    @Column(name = "public_key")
    private String publicKey;

    @Column(name = "keystore")
    private byte[] keyStore;

    @Enumerated(EnumType.STRING)
    @Column(name = "sign_mechanism_name")
    private SignMechanism signMechanismName;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage")
    private KeyUsage usage;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SignerKeyEntity that = (SignerKeyEntity) o;
        return Objects.equals(id, that.id)
                && Objects.equals(externalId, that.externalId)
                && Objects.equals(tokenId, that.tokenId)
                && type == that.type
                && Objects.equals(friendlyName, that.friendlyName)
                && Objects.equals(label, that.label)
                && Objects.equals(publicKey, that.publicKey)
                && Objects.deepEquals(keyStore, that.keyStore)
                && signMechanismName == that.signMechanismName
                && usage == that.usage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, externalId, tokenId, type, friendlyName, label, publicKey,
                Arrays.hashCode(keyStore), signMechanismName, usage);
    }
}
