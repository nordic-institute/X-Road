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
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.RestMessage;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.RequestWrapper;

import com.google.common.primitives.Bytes;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.messagelog.LogMessage;
import org.niis.xroad.messagelog.RestLogMessage;
import org.niis.xroad.messagelog.TimestampRecord;
import org.niis.xroad.proxy.core.addon.messagelog.AbstractLogManager;
import org.niis.xroad.proxy.core.clientproxy.ClientRestMessageProcessor.SigningProxyMessageEntity;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.serverconf.ServerConfProvider;

import javax.net.ssl.SSLHandshakeException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SigningProxyMessageEntityTest {

    private static final String X_REQUEST_ID = "test-x-request-id";
    private static final String CONTENT_TYPE = MimeUtils.mpMixedContentType("xtopTestBoundary");
    private static final int BODY_SIZE = 200_000;
    private static final int FAIL_AFTER_BYTES = 10_000;

    private final MessageSigningService messageSigningService = mock(MessageSigningService.class);
    private final RequestWrapper jRequest = mock(RequestWrapper.class);

    private CountingLogManager logManager;
    private RestRequest restRequest;
    private ClientId.Conf senderId;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        logManager = new CountingLogManager(mock(GlobalConfProvider.class), mock(ServerConfProvider.class));
        MessageLog.init(logManager);

        var chain = mock(CertChain.class);
        when(chain.getAllCertsWithoutTrustedRoot()).thenReturn(List.of());
        when(messageSigningService.getAuthKey()).thenReturn(new AuthKey(chain, null));
        when(messageSigningService.getAllOcspResponses(any())).thenReturn(List.of());
        when(messageSigningService.createSigningCtx(any()))
                .thenReturn(builder -> new SignatureData("<Signature>test-signature</Signature>"));

        senderId = ClientId.Conf.create("XRD", "Class", "Member", "SubSystem");
        restRequest = new RestRequest(
                "POST",
                "/r%d/XRD/Class/Member/SubSystem/ServiceCode".formatted(RestMessage.PROTOCOL_VERSION),
                null,
                List.of(new BasicHeader("X-Road-Client", "XRD/Class/Member/SubSystem"),
                        new BasicHeader("X-Road-Id", "test-query-id")),
                X_REQUEST_ID);
    }

    @Test
    void replayProducesByteIdenticalOutputAndLogsOnce() {
        var body = randomBody();
        var entity = newEntity(body, true);
        var first = new ByteArrayOutputStream();
        var second = new ByteArrayOutputStream();

        entity.writeTo(first);
        entity.writeTo(second);

        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.isReplayable()).isTrue();
        assertThat(second.toByteArray()).isEqualTo(first.toByteArray());
        assertThat(Bytes.indexOf(first.toByteArray(), body)).isNotNegative();
        assertThat(entity.getRestBodyDigest()).isNotNull();
        assertThat(logManager.logCount()).isEqualTo(1);
        assertThat(logManager.loggedBody(0)).isEqualTo(body);
        entity.consume();
    }

    @Test
    void midWriteFailureCompletesMaterializationAndAllowsReplay() {
        var body = randomBody();
        var entity = newEntity(body, true);

        var thrown = catchThrowable(() -> entity.writeTo(new FailingOutputStream(FAIL_AFTER_BYTES)));

        assertThat(thrown).isInstanceOf(XrdRuntimeException.class);
        assertThat(ExceptionUtils.indexOfType(thrown, SSLHandshakeException.class)).isNotEqualTo(-1);
        assertThat(entity.isReplayable()).isTrue();
        // the message was fully materialized despite the dead connection: signed + logged with full body
        assertThat(logManager.logCount()).isEqualTo(1);
        assertThat(logManager.loggedBody(0)).isEqualTo(body);

        var firstReplay = new ByteArrayOutputStream();
        var secondReplay = new ByteArrayOutputStream();
        entity.writeTo(firstReplay);
        entity.writeTo(secondReplay);

        assertThat(Bytes.indexOf(firstReplay.toByteArray(), body)).isNotNegative();
        assertThat(secondReplay.toByteArray()).isEqualTo(firstReplay.toByteArray());
        assertThat(logManager.logCount()).isEqualTo(1);
        entity.consume();
    }

    @Test
    void emptyBodyMaterializesAndReplays() {
        var entity = newEntity(new byte[0], true);
        var first = new ByteArrayOutputStream();
        var second = new ByteArrayOutputStream();

        entity.writeTo(first);
        entity.writeTo(second);

        assertThat(second.toByteArray()).isEqualTo(first.toByteArray());
        assertThat(entity.getRestBodyDigest()).isNull();
        assertThat(logManager.logCount()).isEqualTo(1);
        assertThat(logManager.loggedBody(0)).isNull();
        entity.consume();
    }

    @Test
    void clientStreamFailureBreaksEntityWithoutLogging() {
        when(jRequest.getInputStream()).thenReturn(new FailingInputStream(FAIL_AFTER_BYTES));
        var entity = new SigningProxyMessageEntity(CONTENT_TYPE, messageSigningService, restRequest, senderId,
                jRequest, tempDir.toString(), null, X_REQUEST_ID, true);

        var thrown = catchThrowable(() -> entity.writeTo(new ByteArrayOutputStream()));

        assertThat(thrown).isInstanceOf(XrdRuntimeException.class);
        assertThat(entity.isReplayable()).isFalse();
        assertThat(logManager.logCount()).isZero();
        assertThatThrownBy(() -> entity.writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(XrdRuntimeException.class);
        entity.consume();
    }

    @Test
    void retryDisabledWriteFailurePropagatesWithoutLogging() {
        var entity = newEntity(randomBody(), false);

        var thrown = catchThrowable(() -> entity.writeTo(new FailingOutputStream(FAIL_AFTER_BYTES)));

        assertThat(thrown).isInstanceOf(XrdRuntimeException.class);
        assertThat(ExceptionUtils.indexOfType(thrown, SSLHandshakeException.class)).isNotEqualTo(-1);
        assertThat(entity.isReplayable()).isFalse();
        assertThat(logManager.logCount()).isZero();
        entity.consume();
    }

    private SigningProxyMessageEntity newEntity(byte[] body, boolean retryEnabled) {
        when(jRequest.getInputStream()).thenReturn(new ByteArrayInputStream(body));
        return new SigningProxyMessageEntity(CONTENT_TYPE, messageSigningService, restRequest, senderId,
                jRequest, tempDir.toString(), null, X_REQUEST_ID, retryEnabled);
    }

    private static byte[] randomBody() {
        var body = new byte[BODY_SIZE];
        new Random(42).nextBytes(body);
        return body;
    }

    private static final class CountingLogManager extends AbstractLogManager {
        private final List<byte[]> loggedBodies = new ArrayList<>();

        CountingLogManager(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider) {
            super(globalConfProvider, serverConfProvider);
        }

        @Override
        public void log(LogMessage message) {
            if (message instanceof RestLogMessage rest && rest.getBody() != null) {
                try {
                    loggedBodies.add(rest.getBody().readAllBytes());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                loggedBodies.add(null);
            }
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
            return loggedBodies.size();
        }

        byte[] loggedBody(int index) {
            return loggedBodies.get(index);
        }
    }

    private static final class FailingOutputStream extends OutputStream {
        private int remaining;

        FailingOutputStream(int allowedBytes) {
            this.remaining = allowedBytes;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len > remaining) {
                throw new SSLHandshakeException("Received fatal alert: certificate_required");
            }
            remaining -= len;
        }
    }

    private static final class FailingInputStream extends InputStream {
        private int remaining;

        FailingInputStream(int allowedBytes) {
            this.remaining = allowedBytes;
        }

        @Override
        public int read() throws IOException {
            if (remaining-- <= 0) {
                throw new IOException("client aborted");
            }
            return 'a';
        }
    }
}
