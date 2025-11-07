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
package org.niis.xroad.proxy.core.addon.messagelog;

import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.message.AttachmentStream;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.RestLogMessage;
import ee.ria.xroad.common.messagelog.SoapLogMessage;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CacheInputStream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.niis.xroad.common.messagelog.MessageLogDbProperties;
import org.niis.xroad.common.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.common.rpc.NoopVaultKeyProvider;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.messagelog.archiver.core.LogArchiver;
import org.niis.xroad.messagelog.archiver.core.LogCleaner;
import org.niis.xroad.messagelog.archiver.core.config.LogArchiverExecutionProperties;
import org.niis.xroad.proxy.core.addon.opmonitoring.NoOpMonitoringBuffer;
import org.niis.xroad.proxy.core.configuration.MessageLogDatabaseConfig;
import org.niis.xroad.proxy.core.configuration.ProxyMessageLogProperties;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.proxy.core.addon.messagelog.ProxyTestUtil.getGlobalConf;
import static org.niis.xroad.proxy.core.addon.messagelog.ProxyTestUtil.getServerConf;

@Slf4j
abstract class AbstractMessageLogTest {

    ProxyProperties proxyProperties;
    ProxyMessageLogProperties messageLogProperties;
    OcspVerifierFactory ocspVerifierFactory = new OcspVerifierFactory();
    GlobalConfProvider globalConfProvider;
    KeyConfProvider keyConfProvider;
    TestServerConfWrapper serverConfProvider;
    CommonBeanProxy commonBeanProxy;
    LogRecordManager logRecordManager;
    DatabaseCtx databaseCtx;
    org.niis.xroad.common.messagelog.MessageRecordEncryption messageRecordEncryption;

    LogManager logManager;
    LogArchiverExecutionProperties logArchiverExecutionProperties;

    protected final String archivesDir = "build/archive";
    protected final Path archivesPath = Paths.get(archivesDir);

    private LogArchiver logArchiverRef;
    private LogCleaner logCleanerRef;

    void testSetUp() throws Exception {
        testSetUp(Map.of());
    }

    protected void testSetUp(boolean timestampImmediately) throws Exception {
        testSetUp(Map.of("xroad.proxy.message-log.timestamper.timestamp-immediately", String.valueOf(timestampImmediately)));
    }

    protected void testSetUp(Map<String, String> configOverrides) throws Exception {
        // Initialize ProxyProperties with overrides
        proxyProperties = ConfigUtils.defaultConfiguration(ProxyProperties.class);

        messageLogProperties = configOverrides.isEmpty()
                ? ConfigUtils.defaultConfiguration(ProxyMessageLogProperties.class)
                : ConfigUtils.initConfiguration(ProxyMessageLogProperties.class, configOverrides);

        globalConfProvider = getGlobalConf();
        keyConfProvider = mock(KeyConfProvider.class);
        serverConfProvider = new TestServerConfWrapper(getServerConf());

        Map<String, String> hibernateProperties = Map.of(
                "xroad.db.messagelog.hibernate.connection.driver_class", "org.hsqldb.jdbcDriver",
                "xroad.db.messagelog.hibernate.connection.url", "jdbc:hsqldb:mem:securelog;sql.syntax_pgs=true;",
                "xroad.db.messagelog.hibernate.connection.username", "securelog",
                "xroad.db.messagelog.hibernate.connection.password", "securelog",
                "xroad.db.messagelog.hibernate.hbm2ddl.auto", "create-drop",
                "xroad.db.messagelog.hibernate.hbm2ddl.import_files", "schema.sql",
                "xroad.db.messagelog.hibernate.jdbc.batch_size", "20",
                "xroad.db.messagelog.hibernate.show_sql", "false"
        );
        var props = ConfigUtils.initConfiguration(MessageLogDbProperties.class, hibernateProperties);
        CommonProperties commonProperties = ConfigUtils.initConfiguration(CommonProperties.class, Map.of(
                "xroad.common.temp-files-path", "build/tmp"
        ));
        databaseCtx = MessageLogDatabaseConfig.create(props);
        messageRecordEncryption = new org.niis.xroad.common.messagelog.MessageRecordEncryption(messageLogProperties.databaseEncryption());
        logRecordManager = new LogRecordManager(databaseCtx, messageRecordEncryption);
        var vaultKeyProvider = mock(NoopVaultKeyProvider.class);
        var encryptionConfigProvider = mock(EncryptionConfigProvider.class);
        var keyManager = mock(org.niis.xroad.common.pgp.PgpKeyManager.class);
        var encryptionService = mock(org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService.class);
        commonBeanProxy = new CommonBeanProxy(globalConfProvider, serverConfProvider, keyConfProvider,
                null, null, logRecordManager, vaultKeyProvider, new NoOpMonitoringBuffer(), proxyProperties,
                ocspVerifierFactory, commonProperties, encryptionConfigProvider, messageRecordEncryption);

        if (useTestLogManager()) {
            logManager = new TestLogManager(globalConfProvider, serverConfProvider, logRecordManager, databaseCtx, messageLogProperties);
        } else {
            logManager = new LogManager(globalConfProvider, serverConfProvider, logRecordManager, databaseCtx, messageLogProperties);
        }

        if (!Files.exists(archivesPath)) {
            Files.createDirectory(archivesPath);
        }

        // Initialize logArchiverExecutionProperties from proxyProperties configuration
        var archiverProps = messageLogProperties.archiver();
        var databaseProps = messageLogProperties.databaseEncryption();

        // Create archive encryption properties
        var archiveEncryption = new LogArchiverExecutionProperties.ArchiveEncryptionProperties(
                archiverProps.enabled(),
                archiverProps.defaultKeyId(),
                archiverProps.groupingStrategy(),
                archiverProps.grouping()
        );

        // Create database encryption properties
        var databaseEncryption = new LogArchiverExecutionProperties.DatabaseEncryptionProperties(
                databaseProps.enabled(),
                databaseProps.messagelogKeystoreStr().orElse(null),
                databaseProps.messagelogKeystorePasswordStr().orElse(null),
                databaseProps.messagelogKeyId().orElse(null)
        );

        logArchiverExecutionProperties = new LogArchiverExecutionProperties(
                archiveEncryption,
                databaseEncryption,
                archiverProps.cleanTransactionBatchSize(),
                archiverProps.cleanKeepRecordsFor(),
                archiverProps.transactionBatchSize(),
                archiverProps.archivePath(),
                archiverProps.archiveTransferCommand().orElse(null),
                messageLogProperties.hashAlg(),
                archiverProps.maxFilesize(),
                archiverProps.archivePath()
        );

        logArchiverRef = new TestLogArchiver(keyManager, encryptionService, globalConfProvider, databaseCtx);
        logCleanerRef = new TestLogCleaner(databaseCtx);
    }

    void testTearDown() throws Exception {
        logManager.destroy();
        FileUtils.deleteDirectory(archivesPath.toFile());
    }

    protected boolean useTestLogManager() {
        return false;
    }

    void initLogManager() {
        signalTimestampingStatus(SetTimestampingStatusMessage.Status.SUCCESS);
    }

    /**
     * Sends time stamping status message to LogManager
     *
     * @param status status message
     */
    private void signalTimestampingStatus(SetTimestampingStatusMessage.Status status) {
        logManager.setTimestampingStatus(new SetTimestampingStatusMessage(status));
    }

    protected void log(SoapMessageImpl message, SignatureData signature) throws Exception {
        log(message, signature, List.of());
    }

    protected void log(SoapMessageImpl message, SignatureData signature, List<byte[]> attachments) throws Exception {
        var attachmentStreamList = attachments.stream()
                .map(attachment -> AttachmentStream.fromInputStream(new ByteArrayInputStream(attachment), attachment.length)).toList();
        logManager.log(new SoapLogMessage(message, signature, attachmentStreamList, true, message.getQueryId()));
    }

    protected void log(RestRequest message, SignatureData signatureData, byte[] body)
            throws Exception {
        final ByteArrayInputStream bos = new ByteArrayInputStream(body);
        final CacheInputStream cis = new CacheInputStream(bos, bos.available());

        final RestLogMessage logMessage = new RestLogMessage(message.getQueryId(),
                message.getClientId(),
                message.getServiceId(),
                message, signatureData,
                cis,
                true,
                message.getXRequestId());
        logManager.log(logMessage);
    }

    TimestampRecord timestamp(MessageRecord record) {
        return logManager.timestamp(record.getId());
    }

    void startTimestamping() {
        logManager.taskQueue.handleStartTimestamping();
    }

    void startArchiving() {
        logArchiverRef.execute(logArchiverExecutionProperties);
    }

    void startCleaning() {
        logCleanerRef.execute(logArchiverExecutionProperties);
    }

    static void assertMessageRecord(Object o, String queryId) {
        assertNotNull(o);
        assertTrue(o instanceof MessageRecord);

        MessageRecord messageRecord = (MessageRecord) o;
        assertEquals(queryId, messageRecord.getQueryId());
    }

    static Object waitForMessageInTaskQueue() throws Exception {
        assertTrue(TestTaskQueue.waitForMessage());

        Object message = TestTaskQueue.getLastTimestampResult();
        assertNotNull("Did not get message from task queue", message);

        return message;
    }

    static Timestamper.TimestampSucceeded waitForTimestampSuccessful() throws Exception {
        Object result = waitForMessageInTaskQueue();
        assertTrue("Got " + result, result instanceof Timestamper.TimestampSucceeded);

        return (Timestamper.TimestampSucceeded) result;
    }

}
