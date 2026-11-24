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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.messagelog.MessageLogDatabaseCtx;
import org.niis.xroad.proxy.core.configuration.ProxyMessageLogProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogManagerNodeRoleTest {

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private LogRecordManager logRecordManager;

    @Mock
    private MessageLogDatabaseCtx messageLogDatabaseCtx;

    private final ProxyMessageLogProperties messageLogProperties =
            new ProxyMessageLogProperties(XRoadConfigBuilder.create().register(ProxyConfigKeys.instance()).build());

    @Test
    void startsTimestamperJobOnPrimaryOrStandaloneNode() {
        LogManager logManager;

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(false);
            logManager = newLogManager();
        }

        assertNotNull(logManager.timestamperJob);
        logManager.destroy();
    }

    @Test
    void doesNotStartTimestamperJobOnSecondaryNode() {
        LogManager logManager;

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(true);
            logManager = newLogManager();
        }

        assertNull(logManager.timestamperJob);
        logManager.destroy();
    }

    @Test
    void batchResultDoesNotTouchTheDatabase() {
        LogManager logManager = newPrimaryLogManager(60);

        logManager.setTimestampingStatus(true);
        logManager.setTimestampingStatus(false);

        verifyNoInteractions(messageLogDatabaseCtx);
        logManager.destroy();
    }

    /**
     * The refusal check has no role branch: a primary node refuses exactly like a secondary one
     * when the read of the oldest pending message fails. A long job delay keeps the batch job from
     * running during the test.
     */
    @Test
    void readFailureRefusesMessage() {
        when(serverConfProvider.getTspUrls()).thenReturn(List.of("http://tsa"));

        LogManager logManager = newPrimaryLogManager(60);

        when(messageLogDatabaseCtx.findOldestTimestampTaskTime()).thenThrow(new RuntimeException("read failed"));

        assertThrows(RuntimeException.class, () -> logManager.verifyCanLogMessage(false));

        logManager.destroy();
    }

    /**
     * Every call to verifyCanLogMessage reads the oldest pending message time again; the check
     * never caches the result between calls.
     */
    @Test
    void readsOldestPendingMessageOnEveryCheck() {
        when(serverConfProvider.getTspUrls()).thenReturn(List.of("http://tsa"));

        LogManager logManager = newPrimaryLogManager(60);

        when(messageLogDatabaseCtx.findOldestTimestampTaskTime()).thenReturn(Optional.empty());

        logManager.verifyCanLogMessage(false);
        logManager.verifyCanLogMessage(false);
        logManager.verifyCanLogMessage(false);

        verify(messageLogDatabaseCtx, times(3)).findOldestTimestampTaskTime();
        logManager.destroy();
    }

    private LogManager newLogManager() {
        return new LogManager(globalConfProvider, serverConfProvider, logRecordManager, messageLogDatabaseCtx, messageLogProperties);
    }

    private LogManager newPrimaryLogManager(int acceptableTimestampFailurePeriodSeconds) {
        ProxyMessageLogProperties properties = newProperties(acceptableTimestampFailurePeriodSeconds);

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(false);
            return new LogManager(globalConfProvider, serverConfProvider, logRecordManager, messageLogDatabaseCtx, properties) {
                @Override
                protected Duration getTimestamperJobInitialDelay() {
                    return Duration.ofHours(1);
                }
            };
        }
    }

    private static ProxyMessageLogProperties newProperties(int acceptableTimestampFailurePeriodSeconds) {
        return new ProxyMessageLogProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(Map.of("xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period",
                        String.valueOf(acceptableTimestampFailurePeriodSeconds)))
                .build());
    }
}
