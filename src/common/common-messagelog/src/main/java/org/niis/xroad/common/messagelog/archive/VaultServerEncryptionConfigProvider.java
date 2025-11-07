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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService;
import org.niis.xroad.common.pgp.PgpKeyManager;
import org.niis.xroad.common.pgp.PgpKeyUtils;

import java.util.Collections;
import java.util.List;

/**
 * Vault-based encryption configuration provider for server-level (no grouping) encryption.
 * Uses BouncyCastle and Vault instead of GPG.
 */
@Slf4j
public final class VaultServerEncryptionConfigProvider implements EncryptionConfigProvider {
    private final PgpKeyManager keyManager;
    private final BouncyCastlePgpEncryptionService encryption;

    private volatile EncryptionConfig config;

    public VaultServerEncryptionConfigProvider(PgpKeyManager keyManager, BouncyCastlePgpEncryptionService encryption) {
        this.keyManager = keyManager;
        this.encryption = encryption;
    }

    @Override
    public EncryptionConfig forGrouping(Grouping grouping) {
        return getConfig();
    }

    @Override
    public EncryptionConfig forClientId(ClientId clientId) {
        return getConfig();
    }

    @Override
    public EncryptionConfig forDiagnostics(List<ClientId> members) {
        return getConfig();
    }

    private EncryptionConfig getConfig() {
        initMissing();
        return config;
    }

    private void initMissing() {
        if (config == null) {
            synchronized (this) {
                if (config == null) {
                    try {
                        // For server-level encryption, use the signing key (self-encryption)
                        var signingKeyPair = keyManager.getSigningKeyPair();
                        String keyId = PgpKeyUtils.formatKeyId(signingKeyPair.publicKey().getKeyID());

                        log.info("Vault server encryption config: using signing key {} for self-encryption", keyId);
                        this.config = new VaultEncryptionConfig(encryption, Collections.singleton(keyId), Collections.emptyList());
                    } catch (Exception e) {
                        throw XrdRuntimeException.systemInternalError("Failed to initialize Vault server encryption config", e);
                    }
                }
            }
        }
    }
}
