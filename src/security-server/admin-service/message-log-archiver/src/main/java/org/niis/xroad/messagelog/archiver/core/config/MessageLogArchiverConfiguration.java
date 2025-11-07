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

import ee.ria.xroad.common.db.DatabaseCtx;

import jakarta.inject.Named;
import lombok.Setter;
import org.niis.xroad.common.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.common.messagelog.archive.MessageLogEncryptionConfig;
import org.niis.xroad.common.messagelog.archive.VaultArchivalPgpKeyProvider;
import org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService;
import org.niis.xroad.common.pgp.PgpKeyManager;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.messagelog.archiver.core.LogArchiver;
import org.niis.xroad.messagelog.archiver.core.LogCleaner;
import org.niis.xroad.messagelog.archiver.job.LogArchiverJob;
import org.niis.xroad.messagelog.archiver.job.LogCleanerJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Setter
@Configuration
@ConditionalOnProperty(value = "xroad.message-log-archiver.enabled", havingValue = "true")
public class MessageLogArchiverConfiguration extends MessageLogEncryptionConfig {
    public static final String MESSAGE_LOG_DB_CTX = "messageLogCtx";

    @Bean
    @Named(MESSAGE_LOG_DB_CTX)
    public DatabaseCtx datbaseCtx(MessageLogDatabaseProperties properties) {
        return new DatabaseCtx("messagelog", properties.hibernate());
    }

    @Bean
    public LogArchiver logArchiver(LogArchiverProperties properties, CommonProperties commonProperties,
                                   EncryptionConfigProvider encryptionConfigProvider,
                                   GlobalConfProvider globalConfProvider, @Named(MESSAGE_LOG_DB_CTX) DatabaseCtx databaseCtx) {
        return new LogArchiver(properties, encryptionConfigProvider, commonProperties, globalConfProvider, databaseCtx);
    }

    @Bean
    public LogArchiverJob archiverJob(LogArchiverProperties logArchiverProperties, LogArchiver archiver) {
        return new LogArchiverJob(logArchiverProperties, archiver);
    }

    @Bean
    public LogCleaner logCleaner(LogArchiverProperties properties, @Named(MESSAGE_LOG_DB_CTX) DatabaseCtx databaseCtx) {
        return new LogCleaner(properties, databaseCtx);
    }

    @Bean
    public LogCleanerJob cleanerJob(LogArchiverProperties logArchiverProperties, LogCleaner cleaner) {
        return new LogCleanerJob(logArchiverProperties, cleaner);
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

    @Bean
    @Override
    public EncryptionConfigProvider encryptionConfigProvider(PgpKeyManager keyManager, BouncyCastlePgpEncryptionService encryption) {
        return super.encryptionConfigProvider(keyManager, encryption);
    }
}
