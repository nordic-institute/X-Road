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
package org.niis.xroad.messagelog.archiver.core.config;

import ee.ria.xroad.messagelog.database.MessageLogDatabaseCtx;

import lombok.Setter;
import org.niis.xroad.common.messagelog.archive.MessageLogEncryptionConfig;
import org.niis.xroad.common.messagelog.archive.VaultArchivalPgpKeyProvider;
import org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService;
import org.niis.xroad.common.pgp.PgpKeyManager;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.messagelog.archiver.core.LogArchiver;
import org.niis.xroad.messagelog.archiver.core.LogCleaner;
import org.niis.xroad.messagelog.archiver.core.MessageLogArchiverService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Setter
@Configuration
public class MessageLogArchiverConfiguration extends MessageLogEncryptionConfig {

    @Bean
    public MessageLogDatabaseCtx datbaseCtx(MessageLogDatabaseProperties properties) {
        return new MessageLogDatabaseCtx(properties.hibernate());
    }

    @Bean
    public LogArchiver logArchiver(GlobalConfProvider globalConfProvider, MessageLogDatabaseCtx databaseCtx,
                                   PgpKeyManager keyManager, BouncyCastlePgpEncryptionService encryptionService) {
        return new LogArchiver(keyManager, encryptionService, globalConfProvider, databaseCtx);
    }

    @Bean
    public LogCleaner logCleaner(MessageLogDatabaseCtx databaseCtx) {
        return new LogCleaner(databaseCtx);
    }

    @Bean(destroyMethod = "destroy")
    public MessageLogArchiverService messageLogArchiverService(LogArchiver logArchiver, LogCleaner logCleaner) {
        return new MessageLogArchiverService(logArchiver, logCleaner);
    }

    @Bean
    @Override
    public VaultArchivalPgpKeyProvider keyProvider(VaultClient vaultClient) {
        return super.keyProvider(vaultClient);
    }

    @Bean
    @Override
    public PgpKeyManager keyManager(VaultArchivalPgpKeyProvider vaultClient) {
        return super.keyManager(vaultClient);
    }

    @Bean
    @Override
    public BouncyCastlePgpEncryptionService pgpEncryption(PgpKeyManager keyManager) {
        return super.pgpEncryption(keyManager);
    }

}
