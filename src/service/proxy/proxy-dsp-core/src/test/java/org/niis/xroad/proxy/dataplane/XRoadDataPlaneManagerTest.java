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
package org.niis.xroad.proxy.dataplane;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.signaling.domain.DataFlowPrepareMessage;
import org.eclipse.edc.signaling.domain.DataFlowStartMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class XRoadDataPlaneManagerTest {

    private static final String OWN_ADDRESS = "provider.example.org";
    private static final String SERVERPROXY_ENDPOINT = "https://provider.example.org:5500";

    @Mock
    private DataPlaneServerProperties properties;
    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private ServerConfProvider serverConfProvider;
    @Mock
    private ProxyProperties proxyProperties;

    private XRoadDataPlaneManager manager;

    @BeforeEach
    void setUp() {
        var ownId = SecurityServerId.Conf.create("DEV", "COM", "1234", "SS0");
        lenient().when(serverConfProvider.getIdentifier()).thenReturn(ownId);
        lenient().when(globalConfProvider.getSecurityServerAddress(ownId)).thenReturn(OWN_ADDRESS);
        lenient().when(proxyProperties.sslEnabled()).thenReturn(true);
        lenient().when(proxyProperties.serverProxyPort()).thenReturn(5500);
        manager = new XRoadDataPlaneManager(properties, globalConfProvider, serverConfProvider, proxyProperties);
    }

    @Test
    void startAdvertisesServerproxyEndpointNotSignalingPath() {
        var message = buildStartMessage("flow-1");

        var result = manager.start(message);

        assertThat(result.getDataAddress().getEndpoint()).isEqualTo(SERVERPROXY_ENDPOINT);
        assertThat(result.getDataAddress().getEndpointType()).isEqualTo("https");
    }

    @Test
    void startAdvertisedEndpointDoesNotContainDataflowsPath() {
        var message = buildStartMessage("flow-2");

        var result = manager.start(message);

        assertThat(result.getDataAddress().getEndpoint()).doesNotContain("/api/v1/dataflows");
    }

    @Test
    void startReturnsStartedState() {
        var message = buildStartMessage("flow-3");

        var result = manager.start(message);

        assertThat(result.getState()).isEqualTo(DataFlowStates.STARTED.toString());
    }

    @Test
    void prepareAdvertisesServerproxyEndpoint() {
        var message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("flow-4")
                .transferType("Xrd-PULL")
                .build();

        var result = manager.prepare(message);

        assertThat(result.getDataAddress().getEndpoint()).isEqualTo(SERVERPROXY_ENDPOINT);
        assertThat(result.getDataAddress().getEndpointType()).isEqualTo("https");
        assertThat(result.getState()).isEqualTo(DataFlowStates.PROVISIONED.toString());
    }

    @Test
    void startRejectsNonXrdPullTransferType() {
        var message = DataFlowStartMessage.Builder.newInstance()
                .processId("flow-5")
                .transferType("Http-PUSH")
                .agreementId("agreement-1")
                .datasetId("dataset-1")
                .build();

        assertThatThrownBy(() -> manager.start(message))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("Http-PUSH");
    }

    @Test
    void startedTransitionsFlowToStartedAndReturnsStateOnlyStatus() {
        manager.prepare(DataFlowPrepareMessage.Builder.newInstance()
                .processId("flow-9")
                .transferType("Xrd-PULL")
                .build());

        var result = manager.started("flow-9");

        assertThat(result.getState()).isEqualTo(DataFlowStates.STARTED.toString());
        assertThat(result.getDataAddress()).isNull();
        assertThat(manager.state("flow-9")).isEqualTo(DataFlowStates.STARTED);
    }

    @Test
    void completedTransitionsFlowToCompleted() {
        manager.start(buildStartMessage("flow-10"));

        manager.completed("flow-10");

        assertThat(manager.state("flow-10")).isEqualTo(DataFlowStates.COMPLETED);
    }

    @Test
    void terminateTransitionsFlowToTerminated() {
        manager.start(buildStartMessage("flow-6"));

        manager.terminate("flow-6");

        assertThat(manager.state("flow-6")).isEqualTo(DataFlowStates.TERMINATED);
    }

    @Test
    void suspendTransitionsFlowToSuspended() {
        manager.start(buildStartMessage("flow-7"));

        manager.suspend("flow-7", "maintenance");

        assertThat(manager.state("flow-7")).isEqualTo(DataFlowStates.SUSPENDED);
    }

    @Test
    void stateReturnsFailedForUnknownFlow() {
        assertThat(manager.state("unknown")).isEqualTo(DataFlowStates.FAILED);
    }

    private DataFlowStartMessage buildStartMessage(String processId) {
        return DataFlowStartMessage.Builder.newInstance()
                .processId(processId)
                .transferType("Xrd-PULL")
                .agreementId("agreement-1")
                .datasetId("dataset-1")
                .build();
    }
}
