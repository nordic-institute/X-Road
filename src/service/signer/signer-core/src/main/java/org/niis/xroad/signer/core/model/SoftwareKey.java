/*
 * The MIT License
 *
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

import org.jetbrains.annotations.NotNull;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * @param id                The unique key id.
 * @param tokenId           Reference to the token this key belongs to.
 * @param externalId        The unique key id.
 * @param usage             Key usage info.
 * @param friendlyName      The friendly name of the key.
 * @param label             The label of the key.
 * @param publicKey         The X509 encoded public key.
 * @param signMechanismName Signing (PKCS#11) mechanism name.
 * @param keyStore          P12 key store data.
 * @param createdAt         when the DB record was created
 * @param updatedAt         when the DB record was last updated
 */
public record SoftwareKey(Long id,
                          Long tokenId,
                          String externalId,
                          KeyUsageInfo usage,
                          String friendlyName,
                          String label,
                          String publicKey,
                          SignMechanism signMechanismName,
                          byte[] keyStore,
                          Instant createdAt,
                          Instant updatedAt) implements BasicKeyInfo {
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SoftwareKey that = (SoftwareKey) o;
        return Objects.equals(id, that.id)
                && Objects.equals(tokenId, that.tokenId)
                && Objects.equals(label, that.label)
                && Objects.deepEquals(keyStore, that.keyStore)
                && Objects.equals(publicKey, that.publicKey)
                && Objects.equals(externalId, that.externalId)
                && usage == that.usage
                && Objects.equals(friendlyName, that.friendlyName)
                && signMechanismName == that.signMechanismName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tokenId, externalId, usage, friendlyName, label, publicKey, signMechanismName, Arrays.hashCode(keyStore));
    }


    @Override
    public Optional<byte[]> softwareKeyStore() {
        return Optional.ofNullable(keyStore);
    }

    @NotNull
    @Override
    public String toString() {
        return new StringJoiner(", ", SoftwareKey.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("tokenId=" + tokenId)
                .add("externalId='" + externalId + "'")
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
