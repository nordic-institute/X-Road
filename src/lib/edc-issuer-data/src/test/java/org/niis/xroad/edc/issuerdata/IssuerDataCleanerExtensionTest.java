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

package org.niis.xroad.edc.issuerdata;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;
import org.niis.xroad.test.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_CLEANUP_ENABLED;

class IssuerDataCleanerExtensionTest {

    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration SHORT_INTERVAL = Duration.ofMillis(10);

    private final TransactionContext transactionContext = mock();
    private final ExecutorInstrumentation executorInstrumentation = mock(ExecutorInstrumentation.class, CALLS_REAL_METHODS);
    private final ServiceExtensionContext context = mock();
    private final Monitor monitor = mock();
    private IssuerDataCleanerExtension extension;

    @AfterEach
    void stopExtension() {
        if (extension != null) {
            extension.shutdown();
        }
    }

    @Test
    void shouldScheduleNothingOnSecondaryNodeAndLogAtInfo() {
        extension = extensionOnNode(true);

        extension.initialize(context);
        extension.start();

        verify(monitor).info(contains("secondary"));
        verifyNoInteractions(executorInstrumentation, transactionContext);
        assertThatCode(extension::shutdown).doesNotThrowAnyException();
    }

    @Test
    void shouldRunCleanupAgainstTheStoreOnPrimaryOrStandaloneNode() {
        when(transactionContext.execute(any(TransactionContext.ResultTransactionBlock.class)))
                .thenAnswer(_ -> StoreResult.success(0));
        extension = extensionOnNode(false);

        extension.initialize(context);
        extension.start();

        verify(transactionContext, timeout(5000).atLeastOnce()).execute(any(TransactionContext.ResultTransactionBlock.class));
    }

    @Test
    void shouldScheduleNoJobAndTouchNeitherExecutorNorStoreWhenCleanupIsDisabled() {
        extension = extensionOnNode(false, config(Map.of(ISSUER_DATA_CLEANUP_ENABLED.key(), "false")));
        extension.initialize(context);
        extension.start();
        verify(monitor).info(contains(ISSUER_DATA_CLEANUP_ENABLED.key()));
        verifyNoInteractions(executorInstrumentation, transactionContext);
        assertThatCode(extension::shutdown).doesNotThrowAnyException();
    }

    private IssuerDataCleanerExtension extensionOnNode(boolean secondary) {
        return extensionOnNode(secondary, config(Map.of()));
    }

    private IssuerDataCleanerExtension extensionOnNode(boolean secondary, XRoadConfig xRoadConfig) {
        when(context.getMonitor()).thenReturn(monitor);
        return new IssuerDataCleanerExtension(mock(DataSourceRegistry.class), transactionContext, mock(QueryExecutor.class),
                executorInstrumentation, new MutableClock(START), xRoadConfig, () -> secondary, SHORT_INTERVAL, null, null,
                null);
    }

    private static XRoadConfig config(Map<String, String> stored) {
        return XRoadConfigBuilder.create().register(DspConfigKeys.instance()).overrides(stored).build();
    }
}
