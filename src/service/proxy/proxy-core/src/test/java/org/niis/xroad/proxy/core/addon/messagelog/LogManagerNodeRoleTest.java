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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.configuration.ProxyMessageLogProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class LogManagerNodeRoleTest {

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private LogRecordManager logRecordManager;

    @Mock
    private DatabaseCtx messageLogDatabaseCtx;

    private final ProxyMessageLogProperties messageLogProperties =
            new ProxyMessageLogProperties(XRoadConfigBuilder.create().register(ProxyConfigKeys.instance()).build());

    @Test
    void startsTimestamperJobOnPrimaryNode() {
        LogManager logManager;

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(false);
            logManager = newLogManager();
        }

        assertNotNull(logManager.timestamperJob);
        logManager.destroy();
    }

    @Test
    void startsTimestamperJobOnStandaloneNode() {
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

    private LogManager newLogManager() {
        return new LogManager(globalConfProvider, serverConfProvider, logRecordManager, messageLogDatabaseCtx, messageLogProperties);
    }
}
