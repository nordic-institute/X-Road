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
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.util.HttpHeaders;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import org.bouncycastle.bcpg.BCPGInputStream;
import org.bouncycastle.bcpg.PacketTags;
import org.bouncycastle.bcpg.PublicKeyEncSessionPacket;
import org.eclipse.jetty.http.HttpURI;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.proxy.core.addon.messagelog.TestUtil.cleanUpDatabase;
import static org.niis.xroad.proxy.core.addon.messagelog.TestUtil.createRestRequest;
import static org.niis.xroad.proxy.core.addon.messagelog.TestUtil.createSignature;
import static org.niis.xroad.proxy.core.addon.messagelog.TestUtil.initForTest;

@RunWith(Parameterized.class)
public class AsicContainerClientRequestProcessorTest extends AbstractMessageLogTest {

    @Parameterized.Parameters(name = "encrypted = {0}")
    public static Object[] params() {
        return new Object[]{Boolean.FALSE, Boolean.TRUE};
    }

    @Parameterized.Parameter()
    public boolean encrypted;

    private final ConfClientRpcClient confClientRpcClient = mock(ConfClientRpcClient.class);
    private EncryptionConfigProvider encryptionConfigProvider;


    @Test
    public void assertVerificationConfiguration() {
        final var request = mock(RequestWrapper.class);
        final var response = mock(ResponseWrapper.class);

        final MockOutputStream mockOutputStream = new MockOutputStream();

        final AsicContainerClientRequestProcessor proc =
                new AsicContainerClientRequestProcessor(confClientRpcClient,
                        proxyProperties, globalConfProvider, serverConfProvider, logRecordManager,
                        commonProperties.tempFilesPath(), "/verificationconf", request, response,
                        mock(ClientAuthenticationService.class), mock(EncryptionConfigProvider.class));

        byte[] mockZipResponse = new byte[]{'v', 'e', 'r', 'i', 'f', 'i', 'c', 'a', 't', 'i', 'o', 'n', 'c', 'o', 'n', 'f', 'z', 'i', 'p'};

        when(confClientRpcClient.getVerificationConfZip()).thenReturn(mockZipResponse);
        when(response.getOutputStream()).thenReturn(mockOutputStream);

        proc.process();

        verify(response).setContentType(MimeTypes.ZIP);
        verify(response).putHeader(HttpHeaders.CONTENT_DISPOSITION, "filename=\"verificationconf.zip\"");
        assertArrayEquals(mockZipResponse, mockOutputStream.bos.toByteArray());
    }

    @Test
    public void downloadAsicContainer() throws Exception {

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
        when(request.getParameter(Mockito.eq("xRoadInstance"))).thenReturn(message.getClientId().getXRoadInstance());
        when(request.getParameter(Mockito.eq("memberClass"))).thenReturn(message.getClientId().getMemberClass());
        when(request.getParameter(Mockito.eq("memberCode"))).thenReturn(message.getClientId().getMemberCode());
        when(request.getParameter(Mockito.eq("subsystemCode"))).thenReturn(message.getClientId().getSubsystemCode());
        when(request.getParameter(Mockito.eq("queryId"))).thenReturn(queryId);

        final var response = mock(ResponseWrapper.class);

        final MockOutputStream mockOutputStream = new MockOutputStream();
        when(response.getOutputStream()).thenReturn(mockOutputStream);
        final AsicContainerClientRequestProcessor processor =
                new AsicContainerClientRequestProcessor(confClientRpcClient,
                        proxyProperties, globalConfProvider, serverConfProvider,
                        logRecordManager, commonProperties.tempFilesPath(),
                        "/asic", request, response, mock(ClientAuthenticationService.class), encryptionConfigProvider);

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

    @Test
    public void downloadUniqueAsicContainer() throws Exception {
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
        when(request.getParameter(Mockito.eq("xRoadInstance"))).thenReturn(message.getClientId().getXRoadInstance());
        when(request.getParameter(Mockito.eq("memberClass"))).thenReturn(message.getClientId().getMemberClass());
        when(request.getParameter(Mockito.eq("memberCode"))).thenReturn(message.getClientId().getMemberCode());
        when(request.getParameter(Mockito.eq("subsystemCode"))).thenReturn(message.getClientId().getSubsystemCode());
        when(request.getParameter(Mockito.eq("queryId"))).thenReturn(queryId);
        when(request.getParametersMap()).thenReturn(params);

        final var response = mock(ResponseWrapper.class);

        final MockOutputStream mockOutputStream = new MockOutputStream();
        when(response.getOutputStream()).thenReturn(mockOutputStream);

        final AsicContainerClientRequestProcessor processor =
                new AsicContainerClientRequestProcessor(confClientRpcClient,
                        proxyProperties, globalConfProvider, serverConfProvider,
                        logRecordManager, commonProperties.tempFilesPath(),
                        "/asic", request, response, mock(ClientAuthenticationService.class), encryptionConfigProvider);

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
            Assert.assertEquals(PacketTags.PUBLIC_KEY_ENC_SESSION, is.nextPacketTag());
            final PublicKeyEncSessionPacket packet = (PublicKeyEncSessionPacket) is.readPacket();
            assertNotNull(packet.getEncSessionKey());
        }
    }

    static class MockOutputStream extends OutputStream {

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        @Override
        public void write(byte[] b, int off, int len) {
            bos.write(b, off, len);
        }

        @Override
        public void write(int b) {
            bos.write(b);
        }
    }

    @Before
    public void setUp() throws Exception {
        System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY, "false");
        System.setProperty(MessageLogProperties.ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD, "1800");
        System.setProperty(MessageLogProperties.ARCHIVE_GROUPING, GroupingStrategy.SUBSYSTEM.name());
        System.setProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_KEYS_CONFIG, "build/resources/test/mlog-keys.ini");
        System.setProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_ENABLED, String.valueOf(encrypted));
        System.setProperty(MessageLogProperties.ARCHIVE_GROUPING, GroupingStrategy.MEMBER.name());

        initEncryption();
        initForTest();
        testSetUp();

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
        encryptionConfigProvider = messageLogEncryptionConfig.encryptionConfigProvider(keyManager, pgpEncryptionService);
    }

    /**
     * Cleanup test environment for other tests.
     *
     * @throws Exception in case of any unexpected errors
     */
    @After
    public void tearDown() throws Exception {
        System.clearProperty(MessageLogProperties.MESSAGELOG_ENCRYPTION_ENABLED);
        System.clearProperty(MessageLogProperties.MESSAGELOG_KEYSTORE_PASSWORD);
        System.clearProperty(MessageLogProperties.MESSAGELOG_KEYSTORE);
        System.clearProperty(MessageLogProperties.MESSAGELOG_KEY_ID);
        System.clearProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_ENABLED);

        testTearDown();
        cleanUpDatabase(databaseCtx);
    }

    @Override
    protected boolean useTestLogManager() {
        return true;
    }
}
