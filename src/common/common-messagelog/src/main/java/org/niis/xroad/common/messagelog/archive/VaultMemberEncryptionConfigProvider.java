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
package org.niis.xroad.common.messagelog.archive;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.MessageLogProperties;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService;
import org.niis.xroad.common.pgp.PgpKeyManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vault-based encryption configuration provider for member-level grouping.
 * Uses BouncyCastle and Vault with member-specific key mappings.
 * Supports multiple keys per member for redundancy.
 */
@Slf4j
public final class VaultMemberEncryptionConfigProvider implements EncryptionConfigProvider {
    private final PgpKeyManager keyManager;
    private final BouncyCastlePgpEncryptionService encryption;

    VaultMemberEncryptionConfigProvider(PgpKeyManager keyManager, BouncyCastlePgpEncryptionService encryption) {
        this.keyManager = keyManager;
        this.encryption = encryption;
    }

    @Override
    public EncryptionConfig forClientId(ClientId clientId) throws IOException {
        var grouping = MessageLogProperties.getArchiveGrouping().forClient(clientId);
        return forGrouping(grouping);
    }

    @Override
    public EncryptionConfig forGrouping(Grouping grouping) throws IOException {
        if (grouping.clientId() == null) {
            throw new IllegalArgumentException("Expected a grouping with a client identifier");
        }

        String memberId = grouping.clientId().getMemberId().toShortString();

        try {
            // Get member-specific keys from Vault (may return multiple keys)
            var publicKeys = getPublicKeysForMember(memberId);

            if (publicKeys.isEmpty()) {
                log.warn("No encryption keys found for member {}, archives may not be encrypted properly", memberId);
                return new VaultEncryptionConfig(encryption, Collections.emptySet(), Collections.emptyList());
            }

            // Convert PGPPublicKey objects to key ID strings
            Set<String> keyIds = publicKeys.stream()
                    .map(key -> String.format("%016X", key.getKeyID()))
                    .collect(Collectors.toSet());

            log.debug("Using {} key(s) {} for encrypting member {} archives",
                    keyIds.size(), keyIds, memberId);

            return new VaultEncryptionConfig(encryption, keyIds, Collections.emptyList());

        } catch (PGPException e) {
            throw XrdRuntimeException.systemInternalError("Failed to resolve Vault server encryption config", e);
        }
    }

    @Override
    public EncryptionConfig forDiagnostics(List<ClientId> members) {
        try {
            List<EncryptionMember> encryptionMembers = members.stream()
                    .map(member -> member.getMemberId().toShortString())
                    .distinct()
                    .map(this::getEncryptionMember)
                    .toList();

            return new VaultEncryptionConfig(encryption, Collections.emptySet(), encryptionMembers);
        } catch (Exception e) {
            log.error("Failed to get encryption diagnostics", e);
            return new VaultEncryptionConfig(encryption, Collections.emptySet(), Collections.emptyList());
        }
    }

    private EncryptionMember getEncryptionMember(String memberId) {
        try {
            var publicKeys = getPublicKeysForMember(memberId);

            if (publicKeys.isEmpty()) {
                log.debug("No member-specific keys for {}, using default", memberId);
                return new EncryptionMember(memberId, Collections.emptySet(), true);
            }

            Set<String> keyIds = publicKeys.stream()
                    .map(key -> String.format("%016X", key.getKeyID()))
                    .collect(Collectors.toSet());

            log.debug("Member {} has {} encryption key(s): {}", memberId, keyIds.size(), keyIds);
            return new EncryptionMember(memberId, keyIds, false);

        } catch (Exception e) {
            log.warn("Failed to get keys for member {}: {}", memberId, e.getMessage());
            return new EncryptionMember(memberId, Collections.emptySet(), true);
        }
    }


    /**
     * Gets public keys for a specific member.
     * If member-specific keys are configured, returns ALL of them; otherwise returns the default key.
     * A member can have multiple encryption keys - all are returned for encryption.
     *
     * @param memberId Member identifier in format "INSTANCE/CLASS/CODE"
     * @return List of public keys for encryption (can contain multiple keys)
     */
    public List<PGPPublicKey> getPublicKeysForMember(String memberId) throws IOException, PGPException {

        // Check if member-specific keys are configured (can be multiple)
        Set<String> keyIdsForMember = MessageLogProperties.getKeyMappings().get(memberId);
        if (keyIdsForMember != null && !keyIdsForMember.isEmpty()) {
            List<PGPPublicKey> memberKeys = keyIdsForMember.stream()
                    .map(keyManager::getPublicKey)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

            if (!memberKeys.isEmpty()) {
                log.debug("Using {} member-specific key(s) {} for {}",
                        memberKeys.size(), keyIdsForMember, memberId);
                return memberKeys;
            } else {
                log.warn("Member-specific keys {} not found for {}, falling back to default",
                        keyIdsForMember, memberId);
            }
        }

        // Fall back to default key
        if (MessageLogProperties.getArchiveDefaultEncryptionKey() != null) {
            var defaultKey = keyManager.getPublicKey(MessageLogProperties.getArchiveDefaultEncryptionKey());
            if (defaultKey.isPresent()) {
                return Collections.singletonList(defaultKey.get());
            }
        }

        log.warn("No suitable encryption key found for {}, using all available keys", memberId);
        return new ArrayList<>(keyManager.getAllPublicKeys().values());
    }
}
