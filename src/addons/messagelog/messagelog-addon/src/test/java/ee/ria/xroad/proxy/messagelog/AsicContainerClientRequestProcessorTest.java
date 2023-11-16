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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.archive.GroupingStrategy;
import ee.ria.xroad.proxy.clientproxy.AsicContainerClientRequestProcessor;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.SystemUtils;
import org.bouncycastle.bcpg.BCPGInputStream;
import org.bouncycastle.bcpg.PacketTags;
import org.bouncycastle.bcpg.PublicKeyEncSessionPacket;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static ee.ria.xroad.proxy.messagelog.TestUtil.cleanUpDatabase;
import static ee.ria.xroad.proxy.messagelog.TestUtil.createRestRequest;
import static ee.ria.xroad.proxy.messagelog.TestUtil.createSignature;
import static ee.ria.xroad.proxy.messagelog.TestUtil.initForTest;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
@RunWith(Parameterized.class)
public class AsicContainerClientRequestProcessorTest extends AbstractMessageLogTest {

    @Parameterized.Parameters(name = "encrypted = {0}")
    public static Object[] params() {
        return new Object[] {Boolean.FALSE, Boolean.TRUE};
    }

    @Parameterized.Parameter(0)
    public boolean encrypted;

    @Test
    public void assertVerificationConfiguration() throws IOException {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);

        final MockOutputStream mockOutputStream = new MockOutputStream();
        when(response.getOutputStream()).thenReturn(mockOutputStream);

        final AsicContainerClientRequestProcessor proc = new AsicContainerClientRequestProcessor(
                "/verificationconf", request, response);

        proc.process();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(mockOutputStream.bos.toByteArray()))) {
            ZipEntry entry = zip.getNextEntry();
            assertEquals("verificationconf/CS/shared-params.xml", entry.getName());
            assertArrayEquals(Files.readAllBytes(Path.of("src/test/resources/globalconf/CS/shared-params.xml")), zip.readAllBytes());

            entry = zip.getNextEntry();
            assertEquals("verificationconf/CS/shared-params.xml.metadata", entry.getName());
            assertArrayEquals("{\"configurationVersion\":\"3\"}".getBytes(), zip.readAllBytes());
        }
    }

    @Test
    public void downloadAsicContainer() throws Exception {
        //TODO /usr/bin/gpg is usually not present on macos
        Assume.assumeTrue("OS not supported.", SystemUtils.IS_OS_LINUX);

        final String requestId = UUID.randomUUID().toString();
        final String queryId = "q-" + requestId;
        final RestRequest message = createRestRequest(queryId, requestId);

        final byte[] body = "\"test message body\"".getBytes(StandardCharsets.UTF_8);
        log(message, createSignature(), body);
        startTimestamping();
        waitForTimestampSuccessful();

        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter(Mockito.eq("xRoadInstance"))).thenReturn(message.getClientId().getXRoadInstance());
        when(request.getParameter(Mockito.eq("memberClass"))).thenReturn(message.getClientId().getMemberClass());
        when(request.getParameter(Mockito.eq("memberCode"))).thenReturn(message.getClientId().getMemberCode());
        when(request.getParameter(Mockito.eq("subsystemCode"))).thenReturn(message.getClientId().getSubsystemCode());
        when(request.getParameter(Mockito.eq("queryId"))).thenReturn(queryId);

        final HttpServletResponse response = mock(HttpServletResponse.class);

        final MockOutputStream mockOutputStream = new MockOutputStream();
        when(response.getOutputStream()).thenReturn(mockOutputStream);

        final AsicContainerClientRequestProcessor processor =
                new AsicContainerClientRequestProcessor("/asic", request, response);

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
        //TODO /usr/bin/gpg is usually not present on macos
        Assume.assumeTrue("OS not supported.", SystemUtils.IS_OS_LINUX);

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

        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter(Mockito.eq("xRoadInstance"))).thenReturn(message.getClientId().getXRoadInstance());
        when(request.getParameter(Mockito.eq("memberClass"))).thenReturn(message.getClientId().getMemberClass());
        when(request.getParameter(Mockito.eq("memberCode"))).thenReturn(message.getClientId().getMemberCode());
        when(request.getParameter(Mockito.eq("subsystemCode"))).thenReturn(message.getClientId().getSubsystemCode());
        when(request.getParameter(Mockito.eq("queryId"))).thenReturn(queryId);
        when(request.getParameterMap()).thenReturn(params);

        final HttpServletResponse response = mock(HttpServletResponse.class);

        final MockOutputStream mockOutputStream = new MockOutputStream();
        when(response.getOutputStream()).thenReturn(mockOutputStream);

        final AsicContainerClientRequestProcessor processor =
                new AsicContainerClientRequestProcessor("/asic", request, response);

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

    static class MockOutputStream extends ServletOutputStream {

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            throw new IllegalStateException();
        }

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
        System.setProperty(SystemProperties.CONFIGURATION_PATH, "src/test/resources/globalconf");
        System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY, "false");
        System.setProperty(MessageLogProperties.ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD, "1800");
        System.setProperty(MessageLogProperties.ARCHIVE_INTERVAL, "0 0 0 1 1 ? 2099");
        System.setProperty(MessageLogProperties.CLEAN_INTERVAL, "0 0 0 1 1 ? 2099");

        System.setProperty(MessageLogProperties.ARCHIVE_PATH, archivesPath.toString());
        System.setProperty(MessageLogProperties.ARCHIVE_GROUPING, GroupingStrategy.SUBSYSTEM.name());

        System.setProperty(MessageLogProperties.ARCHIVE_GPG_HOME_DIRECTORY, "build/gpg");
        System.setProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_KEYS_CONFIG, "build/gpg/keys.ini");
        System.setProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_ENABLED, String.valueOf(encrypted));
        System.setProperty(MessageLogProperties.ARCHIVE_GROUPING, GroupingStrategy.MEMBER.name());

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

    /**
     * Cleanup test environment for other tests.
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
        cleanUpDatabase();
    }

    @Override
    protected Class<? extends AbstractLogManager> getLogManagerImpl() {
        return TestLogManager.class;
    }

}
