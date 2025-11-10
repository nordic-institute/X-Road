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

import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.util.HttpHeaders;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import jakarta.annotation.Nonnull;
import org.bouncycastle.bcpg.BCPGInputStream;
import org.bouncycastle.bcpg.PacketTags;
import org.bouncycastle.bcpg.PublicKeyEncSessionPacket;
import org.eclipse.jetty.http.HttpURI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.common.messagelog.archive.GroupingStrategy;
import org.niis.xroad.common.messagelog.archive.MessageLogEncryptionConfig;
import org.niis.xroad.common.pgp.PgpKeyGenerator;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.proxy.core.addon.messagelog.clientproxy.AsicContainerClientRequestProcessor;
import org.niis.xroad.proxy.core.util.ClientAuthenticationService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.proxy.core.addon.messagelog.ProxyTestUtil.cleanUpDatabase;
import static org.niis.xroad.proxy.core.addon.messagelog.ProxyTestUtil.createRestRequest;
import static org.niis.xroad.proxy.core.addon.messagelog.ProxyTestUtil.createSignature;

class AsicContainerClientRequestProcessorTest extends AbstractMessageLogTest {

    private final ConfClientRpcClient confClientRpcClient = mock(ConfClientRpcClient.class);
    private EncryptionConfigProvider encryptionConfigProvider;
    private boolean encrypted;

    @ParameterizedTest(name = "encrypted = {0}")
    @ValueSource(booleans = {false, true})
    void assertVerificationConfiguration(boolean encryptionEnabled) throws Exception {
        setUpWithEncryption(encryptionEnabled);
        final var request = mock(RequestWrapper.class);
        final var response = mock(ResponseWrapper.class);

        final MockOutputStream mockOutputStream = new MockOutputStream();

        final AsicContainerClientRequestProcessor proc =
                new AsicContainerClientRequestProcessor(confClientRpcClient, mock(EncryptionConfigProvider.class),
                        proxyProperties, globalConfProvider, serverConfProvider, logRecordManager,
                        commonProperties.tempFilesPath(), messageRecordEncryption, "/verificationconf", request, response,
                        mock(ClientAuthenticationService.class));

        byte[] mockZipResponse = new byte[]{'v', 'e', 'r', 'i', 'f', 'i', 'c', 'a', 't', 'i', 'o', 'n', 'c', 'o', 'n', 'f', 'z', 'i', 'p'};

        when(confClientRpcClient.getVerificationConfZip()).thenReturn(mockZipResponse);
        when(response.getOutputStream()).thenReturn(mockOutputStream);

        proc.process();

        verify(response).setContentType(MimeTypes.ZIP);
        verify(response).putHeader(HttpHeaders.CONTENT_DISPOSITION, "filename=\"verificationconf.zip\"");
        assertArrayEquals(mockZipResponse, mockOutputStream.bos.toByteArray());
    }

    @ParameterizedTest(name = "encrypted = {0}")
    @ValueSource(booleans = {false, true})
    void downloadAsicContainer(boolean encryptionEnabled) throws Exception {
        setUpWithEncryption(encryptionEnabled);

        final String requestId = UUID.randomUUID().toString();
        final String queryId = "q-" + requestId;
        final RestRequest message = createRestRequest(queryId, requestId);

        final byte[] body = "\"test message body\"".getBytes(StandardCharsets.UTF_8);
        log(message, createSignature(), body);
        startTimestamping();
        waitForTimestampSuccessful();

        final var request = mock(RequestWrapper.class);
        final var httpURI = mock(HttpURI.class);
        when(request.getHttpURI()).thenReturn(httpURI);
        when(request.getParameter("xRoadInstance")).thenReturn(message.getClientId().getXRoadInstance());
        when(request.getParameter("memberClass")).thenReturn(message.getClientId().getMemberClass());
        when(request.getParameter("memberCode")).thenReturn(message.getClientId().getMemberCode());
        when(request.getParameter("subsystemCode")).thenReturn(message.getClientId().getSubsystemCode());
        when(request.getParameter("queryId")).thenReturn(queryId);

        final var response = mock(ResponseWrapper.class);

        final MockOutputStream mockOutputStream = new MockOutputStream();
        when(response.getOutputStream()).thenReturn(mockOutputStream);
        final AsicContainerClientRequestProcessor processor =
                new AsicContainerClientRequestProcessor(confClientRpcClient, encryptionConfigProvider,
                        proxyProperties, globalConfProvider, serverConfProvider,
                        logRecordManager, commonProperties.tempFilesPath(), messageRecordEncryption,
                        "/asic", request, response, mock(ClientAuthenticationService.class));


        processor.process();


        if (encrypted) {
            // sanity check, we are excepting a gpg encrypted archive
            assertPGPStream(mockOutputStream);
        } else {
            try (ZipInputStream zip = new ZipInputStream(
                    new ByteArrayInputStream(mockOutputStream.bos.toByteArray()))) {
                ZipEntry e;
                int count = 0;
                while ((e = zip.getNextEntry()) != null) {
                    assertTrue(e.getName().startsWith(queryId));
                    count++;
                }
                assertEquals(1, count);
            }
        }
    }

    @ParameterizedTest(name = "encrypted = {0}")
    @ValueSource(booleans = {false, true})
    void downloadUniqueAsicContainer(boolean encryptionEnabled) throws Exception {
        setUpWithEncryption(encryptionEnabled);
        final String requestId = UUID.randomUUID().toString();
        final String queryId = "q-" + requestId;
        final RestRequest message = createRestRequest(queryId, requestId);

        final byte[] body = "\"test message body\"".getBytes(StandardCharsets.UTF_8);
        log(message, createSignature(), body);
        startTimestamping();
        waitForTimestampSuccessful();

        Map<String, String[]> params = new HashMap<>();
        params.put("unique", null);
        params.put("requestOnly", null);

        final var request = mock(RequestWrapper.class);
        final var httpURI = mock(HttpURI.class);
        when(request.getHttpURI()).thenReturn(httpURI);
        when(request.getParameter("xRoadInstance")).thenReturn(message.getClientId().getXRoadInstance());
        when(request.getParameter("memberClass")).thenReturn(message.getClientId().getMemberClass());
        when(request.getParameter("memberCode")).thenReturn(message.getClientId().getMemberCode());
        when(request.getParameter("subsystemCode")).thenReturn(message.getClientId().getSubsystemCode());
        when(request.getParameter("queryId")).thenReturn(queryId);
        when(request.getParametersMap()).thenReturn(params);

        final var response = mock(ResponseWrapper.class);

        final MockOutputStream mockOutputStream = new MockOutputStream();
        when(response.getOutputStream()).thenReturn(mockOutputStream);

        final AsicContainerClientRequestProcessor processor =
                new AsicContainerClientRequestProcessor(confClientRpcClient, encryptionConfigProvider,
                        proxyProperties, globalConfProvider, serverConfProvider,
                        logRecordManager, commonProperties.tempFilesPath(), messageRecordEncryption,
                        "/asic", request, response, mock(ClientAuthenticationService.class));


        processor.process();

        if (encrypted) {
            // sanity check, we are excepting a gpg encrypted archive
            assertPGPStream(mockOutputStream);
        } else {
            try (ZipInputStream zip = new ZipInputStream(
                    new ByteArrayInputStream(mockOutputStream.bos.toByteArray()))) {
                ZipEntry e;
                int count = 0;
                while ((e = zip.getNextEntry()) != null) {
                    if (e.getName().equals("attachment1")) {
                        count++;
                    }
                }
                assertEquals(1, count);
            }
        }
    }

    private void assertPGPStream(MockOutputStream mockOutputStream)
            throws IOException {
        try (BCPGInputStream is = new BCPGInputStream(
                new ByteArrayInputStream(mockOutputStream.bos.toByteArray()))) {
            assertEquals(PacketTags.PUBLIC_KEY_ENC_SESSION, is.nextPacketTag());
            final PublicKeyEncSessionPacket packet = (PublicKeyEncSessionPacket) is.readPacket();
            assertNotNull(packet.getEncSessionKey());
        }
    }

    static class MockOutputStream extends OutputStream {

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        @Override
        public void write(@Nonnull byte[] b, int off, int len) {
            bos.write(b, off, len);
        }

        @Override
        public void write(int b) {
            bos.write(b);
        }
    }

    private void setUpWithEncryption(boolean encryptionEnabled) throws Exception {
        this.encrypted = encryptionEnabled;
        initEncryption();

        // Initialize with test-specific configuration
        Map<String, String> config = new java.util.HashMap<>();
        config.put("xroad.proxy.message-log.timestamper.timestamp-immediately", "false");
        config.put("xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period", "1800");
        config.put("xroad.proxy.message-log.archiver.grouping-strategy", GroupingStrategy.MEMBER.name());
        config.put("xroad.proxy.message-log.archiver.encryption-enabled", String.valueOf(encrypted));

        testSetUp(config);

        // initialize states
        initLogManager();
        TestLogManager.initSetTimestampingStatusLatch();
        TestTaskQueue.initGateLatch();
        TestTaskQueue.initTimestampSavedLatch();

        TestTaskQueue.throwWhenSavingTimestamp = null;

        TestTimestamperWorker.failNextTimestamping(false);
    }

    private void initEncryption() {
        var generator = new PgpKeyGenerator();

        var key = generator.generate("INSTANCE/memberClass/memberCode");
        var vaultClient = mock(VaultClient.class);
        when(vaultClient.getMessageLogArchivalSigningSecretKey()).thenReturn(Optional.of(key.secretData()));
        when(vaultClient.getMessageLogArchivalEncryptionPublicKeys()).thenReturn(Optional.of(key.publicData()));

        var messageLogEncryptionConfig = new MessageLogEncryptionConfig();
        var keyProvider = messageLogEncryptionConfig.keyProvider(vaultClient);
        var keyManager = messageLogEncryptionConfig.keyManager(keyProvider);
        var pgpEncryptionService = messageLogEncryptionConfig.pgpEncryption(keyManager);
        var messageLogProperties = mock(org.niis.xroad.common.messagelog.MessageLogArchivalProperties.class);
        when(messageLogProperties.encryptionEnabled()).thenReturn(encrypted);
        when(messageLogProperties.defaultKeyId()).thenReturn(Optional.empty());
        when(messageLogProperties.groupingStrategy()).thenReturn(GroupingStrategy.NONE);
        when(messageLogProperties.grouping()).thenReturn(Map.of());
        encryptionConfigProvider = messageLogEncryptionConfig.encryptionConfigProvider(keyManager, pgpEncryptionService,
                messageLogProperties);
    }

    /**
     * Cleanup test environment for other tests.
     *
     * @throws Exception in case of any unexpected errors
     */
    @AfterEach
    void tearDown() throws Exception {
        testTearDown();
        cleanUpDatabase(databaseCtx);
    }

    @Override
    protected boolean useTestLogManager() {
        return true;
    }
}
