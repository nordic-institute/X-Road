/*
 * The MIT License
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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.StaxEventSoapParserImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.messagelog.LogMessage;
import org.niis.xroad.messagelog.TimestampRecord;
import org.niis.xroad.proxy.core.addon.messagelog.AbstractLogManager;
import org.niis.xroad.proxy.core.clientproxy.ClientSoapMessageProcessor.ReplaySoapProxyMessageEntity;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.ClientSoapRequestContext;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplaySoapProxyMessageEntityTest {

    private static final Path QUERIES_DIR = Path.of("src/test/queries");
    private static final String X_REQUEST_ID = "test-x-request-id";

    private final MessageSigningService messageSigningService = mock(MessageSigningService.class);
    private final OpMonitoringDataHelper opMonitoringDataHelper = mock(OpMonitoringDataHelper.class);
    private final ProxyProperties proxyProperties = mock(ProxyProperties.class);

    private CountingLogManager logManager;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        logManager = new CountingLogManager(mock(GlobalConfProvider.class), mock(ServerConfProvider.class));
        MessageLog.init(logManager);
        when(messageSigningService.createSigningCtx(any()))
                .thenReturn(builder -> new SignatureData("<Signature>test-signature</Signature>"));
        when(proxyProperties.sslEnabled()).thenReturn(false);
    }

    @Test
    void replayIsByteIdenticalToLiveOutput() throws Exception {
        runScenario("getstate.query", MimeTypes.TEXT_XML_UTF8);
    }

    @Test
    void replayWithAttachmentIsByteIdenticalToLiveOutput() throws Exception {
        runScenario("attachm.query", "multipart/related; charset=UTF-8; boundary=jetty771207119h3h10dty");
    }

    private void runScenario(String queryFile, String contentType) throws Exception {
        try (var ctx = new ClientSoapRequestContext(mock(RequestWrapper.class), mock(ResponseWrapper.class), null)) {
            var decoder = new SoapRequestDecoder(ctx, messageSigningService, tempDir.toString(), X_REQUEST_ID,
                    proxyProperties, opMonitoringDataHelper);
            var liveOutput = encodeThroughPipe(ctx, decoder, queryFile, contentType);

            var entity = new ReplaySoapProxyMessageEntity(decoder);
            var firstReplay = new ByteArrayOutputStream();
            var secondReplay = new ByteArrayOutputStream();
            entity.writeTo(firstReplay);
            entity.writeTo(secondReplay);

            assertThat(entity.isRepeatable()).isTrue();
            assertThat(firstReplay.toByteArray()).isEqualTo(liveOutput);
            assertThat(secondReplay.toByteArray()).isEqualTo(liveOutput);
            assertThat(logManager.logCount()).isEqualTo(1);
        }
    }

    /**
     * Runs the SOAP message decoding exactly as the production handler thread does: the message is
     * parsed and encoded into the per-request pipe while a concurrent reader drains the pipe — here
     * into a byte array, which becomes the reference "live" output of the first send attempt.
     */
    private byte[] encodeThroughPipe(ClientSoapRequestContext ctx, SoapRequestDecoder decoder,
                                     String queryFile, String contentType) throws Exception {
        var liveOutput = new ByteArrayOutputStream();
        Thread drainer = Thread.ofVirtual().start(() -> {
            try {
                ctx.reqIns().transferTo(liveOutput);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try (decoder; var in = Files.newInputStream(QUERIES_DIR.resolve(queryFile))) {
            new SoapMessageDecoder(contentType, decoder, new StaxEventSoapParserImpl()).parse(in);
        }
        drainer.join();
        if (decoder.getException() != null) {
            throw decoder.getException();
        }
        return liveOutput.toByteArray();
    }

    private static final class CountingLogManager extends AbstractLogManager {
        private final AtomicInteger count = new AtomicInteger();

        CountingLogManager(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider) {
            super(globalConfProvider, serverConfProvider);
        }

        @Override
        public void log(LogMessage message) {
            count.incrementAndGet();
        }

        @Override
        public TimestampRecord timestamp(Long messageRecordId) {
            return null;
        }

        @Override
        public Map<String, DiagnosticsStatus> getDiagnosticStatus() {
            return Map.of();
        }

        int logCount() {
            return count.get();
        }
    }
}
