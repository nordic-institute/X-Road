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

import org.niis.xroad.common.messagelog.MessageLogArchivalProperties;
import org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService;
import org.niis.xroad.common.pgp.PgpKeyManager;

import java.io.IOException;
import java.util.List;

/**
 * A strategy interface for archive encryption configuration providers.
 * Uses Vault-based BouncyCastle encryption for PGP operations.
 *
 * @see DisabledEncryptionConfigProvider
 * @see VaultServerEncryptionConfigProvider
 * @see VaultMemberEncryptionConfigProvider
 */
public interface EncryptionConfigProvider {

    default boolean isEncryptionEnabled() {
        return true;
    }

    /**
     * Given a grouping, returns an encryption configuration that applies to it.
     */
    EncryptionConfig forGrouping(Grouping grouping) throws IOException;


    /**
     * Given a grouping, returns an encryption configuration that applies to it.
     */
    EncryptionConfig forClientId(ClientId clientId) throws IOException;

    /**
     * Returns encryption info for diagnostics
     */
    EncryptionConfig forDiagnostics(List<ClientId> members);


    static EncryptionConfigProvider create(PgpKeyManager keyManager,
                                           BouncyCastlePgpEncryptionService encryption,
                                           MessageLogArchivalProperties messageLogArchivalProperties) {
        if (messageLogArchivalProperties.enabled()) {
            return switch (messageLogArchivalProperties.groupingStrategy()) {
                case MEMBER, SUBSYSTEM -> new VaultMemberEncryptionConfigProvider(keyManager, encryption, messageLogArchivalProperties);
                default -> new VaultServerEncryptionConfigProvider(keyManager, encryption);

            };
        }

        return new DisabledEncryptionConfigProvider();
    }
}

