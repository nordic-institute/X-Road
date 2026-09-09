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
package org.niis.xroad.securityserver.restapi.service;

import com.google.common.util.concurrent.MoreExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogInvalidationNotifierTest {

    @Mock
    private ControlPlaneProvisioningClient controlPlaneProvisioningClient;
    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private Dataspace dataspace;

    private CatalogInvalidationNotifier notifier;

    @BeforeEach
    void setUp() {
        lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        notifier = new CatalogInvalidationNotifier(
                controlPlaneProvisioningClient, adminServiceProperties, MoreExecutors.newDirectExecutorService());
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void invalidateCatalogCachesCallsControlPlaneWhenDataspaceEnabledAndNoTransactionActive() {
        when(dataspace.isEnabled()).thenReturn(true);

        notifier.invalidateCatalogCaches();

        verify(controlPlaneProvisioningClient).invalidateCatalogCaches();
    }

    @Test
    void invalidateCatalogCachesIsNoOpWhenDataspaceDisabled() {
        when(dataspace.isEnabled()).thenReturn(false);

        notifier.invalidateCatalogCaches();

        verify(controlPlaneProvisioningClient, never()).invalidateCatalogCaches();
    }

    @Test
    void invalidateCatalogCachesSwallowsAndLogsFailure() {
        when(dataspace.isEnabled()).thenReturn(true);
        doThrow(new RuntimeException("control plane unreachable"))
                .when(controlPlaneProvisioningClient).invalidateCatalogCaches();

        assertThatCode(() -> notifier.invalidateCatalogCaches()).doesNotThrowAnyException();
    }

    @Test
    void invalidateCatalogCachesDefersCallUntilTransactionCommitsWhenTransactionActive() {
        when(dataspace.isEnabled()).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        notifier.invalidateCatalogCaches();
        verify(controlPlaneProvisioningClient, never()).invalidateCatalogCaches();

        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

        verify(controlPlaneProvisioningClient).invalidateCatalogCaches();
    }

    @Test
    void invalidateCatalogCachesNeverCallsControlPlaneWhenTransactionRollsBack() {
        when(dataspace.isEnabled()).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        notifier.invalidateCatalogCaches();
        // afterCompletion(ROLLED_BACK) is delivered on rollback instead of afterCommit; the
        // synchronization is simply never told to commit, so the call is never dispatched.
        TransactionSynchronizationManager.clearSynchronization();

        verify(controlPlaneProvisioningClient, never()).invalidateCatalogCaches();
    }

    @Test
    void invalidateCatalogCachesRunsOffTheCallerThreadWithTheRealExecutor() throws Exception {
        when(dataspace.isEnabled()).thenReturn(true);
        var callingThread = new AtomicReference<Thread>();
        var called = new CountDownLatch(1);
        doAnswer(invocation -> {
            callingThread.set(Thread.currentThread());
            called.countDown();
            return null;
        }).when(controlPlaneProvisioningClient).invalidateCatalogCaches();
        var realNotifier = new CatalogInvalidationNotifier(controlPlaneProvisioningClient, adminServiceProperties);
        try {
            realNotifier.invalidateCatalogCaches();

            assertThat(called.await(5, TimeUnit.SECONDS))
                    .as("control plane notified within the wait budget")
                    .isTrue();
            assertThat(callingThread.get())
                    .as("dispatch runs on the notifier's own thread, not the caller's")
                    .isNotEqualTo(Thread.currentThread());
            assertThat(callingThread.get().isDaemon())
                    .as("the notifier thread must not block JVM shutdown")
                    .isTrue();
        } finally {
            realNotifier.destroy();
        }
    }

    @Test
    void invalidateCatalogCachesIsBestEffortAfterShutdown() {
        when(dataspace.isEnabled()).thenReturn(true);
        var realNotifier = new CatalogInvalidationNotifier(controlPlaneProvisioningClient, adminServiceProperties);
        realNotifier.destroy();

        assertThatCode(realNotifier::invalidateCatalogCaches)
                .as("a rejected dispatch is logged, never thrown to the caller")
                .doesNotThrowAnyException();
    }
}
