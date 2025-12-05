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
package org.niis.xroad.messagelog.archive;

import jakarta.enterprise.context.ApplicationScoped;
import org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService;
import org.niis.xroad.common.pgp.PgpKeyManager;
import org.niis.xroad.common.pgp.PgpKeyResolver;
import org.niis.xroad.common.pgp.StreamingPgpEncryptor;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.messagelog.MessageLogArchivalProperties;
import org.niis.xroad.messagelog.MessageLogDatabaseEncryptionProperties;
import org.niis.xroad.messagelog.MessageRecordEncryption;

public class MessageLogEncryptionConfig {

    @ApplicationScoped
    public VaultArchivalPgpKeyProvider keyProvider(VaultClient vaultClient) {
        return new VaultArchivalPgpKeyProvider(vaultClient);
    }

    @ApplicationScoped
    public PgpKeyManager keyManager(VaultArchivalPgpKeyProvider vaultClient) {
        return new PgpKeyManager(vaultClient);
    }

    @ApplicationScoped
    public BouncyCastlePgpEncryptionService pgpEncryption(PgpKeyManager keyManager) {
        var keyResolver = new PgpKeyResolver(keyManager);
        var encryptor = new StreamingPgpEncryptor();

        return new BouncyCastlePgpEncryptionService(keyManager, keyResolver, encryptor);
    }

    @ApplicationScoped
    public EncryptionConfigProvider encryptionConfigProvider(PgpKeyManager keyManager,
                                                             BouncyCastlePgpEncryptionService encryption,
                                                             MessageLogArchivalProperties messageLogArchivalProperties) {
        return EncryptionConfigProvider.create(keyManager, encryption, messageLogArchivalProperties);
    }

    @ApplicationScoped
    public MessageRecordEncryption messageRecordEncryption(MessageLogDatabaseEncryptionProperties encryptionProperties,
                                                          VaultClient vaultClient) {
        return new MessageRecordEncryption(encryptionProperties, vaultClient);
    }
}
