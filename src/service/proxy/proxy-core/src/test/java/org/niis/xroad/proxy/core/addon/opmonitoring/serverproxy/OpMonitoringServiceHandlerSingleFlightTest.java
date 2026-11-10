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
package org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.opmonitor.api.AccessScope;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.QueryOperationalDataResp;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.serverproxy.ServiceHandlerResult;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy.OpMonitoringServiceHandlerImplTest.globalConfProvider;
import static org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy.OpMonitoringServiceHandlerImplTest.proxyMessage;

/**
 * Concurrent first queries share one in-flight creation of the op-monitor client. The creation is held open by a latch
 * and the callers are released only once each of them is parked in the client accessor.
 */
@Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class OpMonitoringServiceHandlerSingleFlightTest {
    private static final int CALLERS = 4;
    private static final String CLIENT_ACCESSOR = "opMonitorClient";
    private static final String TRUST_FAILURE_DETAILS = "op-monitor CA could not be read";

    private final AtomicInteger creations = new AtomicInteger();
    private final CountDownLatch creationStarted = new CountDownLatch(1);
    private final CountDownLatch creationMayFinish = new CountDownLatch(1);
    private final OpMonitorClient client = mock(OpMonitorClient.class);
    private final List<Caller> callers = new ArrayList<>();
    private OpMonitoringServiceHandlerImpl handler;

    @AfterEach
    void releaseCreation() {
        creationMayFinish.countDown();
    }

    @Test
    void concurrentCallersShareOneFailedCreationAndTheNextCallCreatesAgain() throws Exception {
        when(client.queryOperationalData(any(), any(), any(AccessScope.class))).thenReturn(response());
        handler = handlerCreatingBy(creation -> {
            if (creation == 1) {
                throw XrdRuntimeException.systemException(ErrorCode.OP_MONITOR_TRUST_FAILURE)
                        .details(TRUST_FAILURE_DETAILS)
                        .build();
            }
            return client;
        });
        startCallers(CALLERS);
        creationStarted.await();
        awaitParkedInClientAccessor();

        creationMayFinish.countDown();
        joinCallers();

        assertThat(creations).hasValue(1);
        assertThat(callers).allSatisfy(caller -> assertThat(caller.failure).isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
            assertThat(e.getCode()).isEqualTo(X_SERVICE_FAILED_X + "." + ErrorCode.OP_MONITOR_TRUST_FAILURE.code());
            assertThat(e.getDetails()).isEqualTo(TRUST_FAILURE_DETAILS);
        }));
        assertThat(callers.stream().map(caller -> ((XrdRuntimeException) caller.failure).getIdentifier()).collect(Collectors.toSet()))
                .hasSize(1);

        var next = new Caller(proxyMessage());
        next.run();

        assertThat(next.failure).isNull();
        assertThat(creations).hasValue(2);
    }

    @Test
    void anErrorDuringCreationReachesTheCreatorWaitersFailAndTheNextCallCreatesAgain() throws Exception {
        var error = new LinkageError("op-monitor client could not be linked");
        when(client.queryOperationalData(any(), any(), any(AccessScope.class))).thenReturn(response());
        handler = handlerCreatingBy(creation -> {
            if (creation == 1) {
                throw error;
            }
            return client;
        });
        startCallers(CALLERS);
        creationStarted.await();
        awaitParkedInClientAccessor();

        creationMayFinish.countDown();
        joinCallers();

        assertThat(creations).hasValue(1);
        assertThat(callers).filteredOn(caller -> caller.failure == error).hasSize(1);
        assertThat(callers).filteredOn(caller -> caller.failure != error)
                .hasSize(CALLERS - 1)
                .allSatisfy(caller -> assertThat(caller.failure).isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(X_SERVICE_FAILED_X + "." + ErrorCode.INTERNAL_ERROR.code())));

        var next = new Caller(proxyMessage());
        next.run();

        assertThat(next.failure).isNull();
        assertThat(creations).hasValue(2);
    }

    @Test
    void concurrentCallersShareOneSuccessfulCreationAndItIsKept() throws Exception {
        handler = handlerCreatingBy(_ -> client);
        when(client.queryOperationalData(any(), any(), any(AccessScope.class))).thenReturn(response());
        startCallers(CALLERS);
        creationStarted.await();
        awaitParkedInClientAccessor();

        creationMayFinish.countDown();
        joinCallers();

        assertThat(callers).allSatisfy(caller -> assertThat(caller.failure).isNull());
        assertThat(creations).hasValue(1);
        verify(client, times(CALLERS)).queryOperationalData(any(), any(), any(AccessScope.class));

        var later = new Caller(proxyMessage());
        later.run();

        assertThat(later.failure).isNull();
        assertThat(creations).hasValue(1);
        verify(client, times(CALLERS + 1)).queryOperationalData(any(), any(), any(AccessScope.class));
    }

    @Test
    void destroyDoesNotWaitForACreationInFlightAndTheClientCreatedAfterwardsIsClosedAndNotKept() throws Exception {
        handler = handlerCreatingBy(_ -> client);
        when(client.queryOperationalData(any(), any(), any(AccessScope.class))).thenReturn(response());
        startCallers(1);
        creationStarted.await();

        handler.destroy();

        verify(client, never()).close();

        creationMayFinish.countDown();
        joinCallers();

        verify(client).close();
        assertThat(creations).hasValue(1);
        assertThatThrownBy(() -> handler.startHandling(null, proxyMessage(), opMonitoringData()))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(X_SERVICE_FAILED_X + "." + ErrorCode.INTERNAL_ERROR.code()));
        assertThat(creations).hasValue(1);
    }

    @Test
    void destroyClosesTheCreatedClientAndALaterCallDoesNotCreateAnother() throws Exception {
        handler = handlerCreatingBy(_ -> client);
        when(client.queryOperationalData(any(), any(), any(AccessScope.class))).thenReturn(response());
        creationMayFinish.countDown();
        var first = new Caller(proxyMessage());
        first.run();
        assertThat(first.failure).isNull();

        handler.destroy();

        verify(client).close();
        assertThatThrownBy(() -> handler.startHandling(null, proxyMessage(), opMonitoringData()))
                .isInstanceOf(XrdRuntimeException.class);
        assertThat(creations).hasValue(1);
    }

    private OpMonitoringServiceHandlerImpl handlerCreatingBy(IntFunction<OpMonitorClient> outcome) {
        return new OpMonitoringServiceHandlerImpl(mock(ServerConfProvider.class), globalConfProvider(), () -> {
            int creation = creations.incrementAndGet();
            creationStarted.countDown();
            try {
                creationMayFinish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return outcome.apply(creation);
        });
    }

    private void startCallers(int count) throws IOException {
        for (int i = 0; i < count; i++) {
            callers.add(new Caller(proxyMessage()));
        }
        callers.forEach(Thread::start);
    }

    private void joinCallers() throws InterruptedException {
        for (var caller : callers) {
            caller.join();
        }
    }

    private void awaitParkedInClientAccessor() {
        for (var caller : callers) {
            while (!isParkedInClientAccessor(caller)) {
                Thread.yield();
            }
        }
    }

    private static boolean isParkedInClientAccessor(Thread thread) {
        var state = thread.getState();
        return (state == Thread.State.WAITING || state == Thread.State.BLOCKED)
                && Arrays.stream(thread.getStackTrace()).anyMatch(frame -> frame.getMethodName().equals(CLIENT_ACCESSOR));
    }

    private static QueryOperationalDataResp response() {
        return QueryOperationalDataResp.newBuilder()
                .setResponse(ByteString.copyFromUtf8("<response/>"))
                .setContentType("text/xml;charset=utf-8")
                .build();
    }

    private static OpMonitoringData opMonitoringData() {
        return new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L);
    }

    private final class Caller extends Thread {
        private final ProxyMessage message;
        private volatile ServiceHandlerResult result;
        private volatile Throwable failure;

        private Caller(ProxyMessage message) {
            this.message = message;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                result = handler.startHandling(null, message, opMonitoringData());
            } catch (Throwable e) {
                failure = e;
            }
        }
    }
}
