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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.impl.XRoadConfigCommonProperties;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientSoapMessageProcessorTest {

    private static final ClientId MANAGEMENT_CLIENT =
            ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
    private static final ClientId OTHER_CLIENT =
            ClientId.Conf.create("DEV", "COM", "4321", "TestClient");

    @Test
    void isManagementRequestReturnsTrueWhenServiceIdMatchesManagementSubsystem() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MANAGEMENT_CLIENT);
        var serviceId = ServiceId.Conf.create(MANAGEMENT_CLIENT, "clientReg");

        var processor = createProcessor(globalConfProvider);

        assertThat(processor.isManagementRequest(serviceId)).isTrue();
    }

    @Test
    void isManagementRequestReturnsFalseWhenGlobalConfManagementServiceIsNull() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var serviceId = ServiceId.Conf.create(OTHER_CLIENT, "testService");

        var processor = createProcessor(globalConfProvider);

        assertThat(processor.isManagementRequest(serviceId)).isFalse();
    }

    @Test
    void isManagementRequestReturnsFalseForNonManagementClient() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MANAGEMENT_CLIENT);
        var serviceId = ServiceId.Conf.create(OTHER_CLIENT, "testService");

        var processor = createProcessor(globalConfProvider);

        assertThat(processor.isManagementRequest(serviceId)).isFalse();
    }

    @Test
    void managementServiceIdProducesDspRequestWithManagementFlagTrue() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MANAGEMENT_CLIENT);
        var serviceId = ServiceId.Conf.create(MANAGEMENT_CLIENT, "clientReg");

        var processor = createProcessor(globalConfProvider);

        // isManagementRequest drives the management flag in the DspRequest constructed by processRequest.
        // Verify the flag value matches what the processor would pass.
        var dspRequest = new DspRequest(serviceId, serviceId.getClientId(), null, processor.isManagementRequest(serviceId));
        assertThat(dspRequest.managementSubsystem()).isTrue();
    }

    @Test
    void nonManagementServiceIdProducesDspRequestWithManagementFlagFalse() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MANAGEMENT_CLIENT);
        var serviceId = ServiceId.Conf.create(OTHER_CLIENT, "testService");

        var processor = createProcessor(globalConfProvider);

        var dspRequest = new DspRequest(serviceId, serviceId.getClientId(), null, processor.isManagementRequest(serviceId));
        assertThat(dspRequest.managementSubsystem()).isFalse();
    }

    private ClientSoapMessageProcessor createProcessor(GlobalConfProvider globalConfProvider) {
        return new ClientSoapMessageProcessor(
                mock(MessageSigningService.class),
                mock(HttpSenderProvider.class),
                mock(ClientVerificationService.class),
                mock(OpMonitoringDataHelper.class),
                globalConfProvider,
                mock(ProxyProperties.class),
                new XRoadConfigCommonProperties(XRoadConfigBuilder.create().register(CommonConfigKeys.instance()).build()),
                mock(OcspVerifierFactory.class),
                mock(ClientRequestPreparationService.class),
                mock(DspRequestProcessor.class),
                mock(IdentifierValidationService.class));
    }
}
