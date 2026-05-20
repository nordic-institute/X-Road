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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.SneakyThrows;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.server.Request;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.RestRequestContext;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.net.URI;
import java.util.Map;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_CLIENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.CLIENT;

class ClientRestMessageProcessorTest {

    @SneakyThrows
    @Test
    void processShouldAddOpMonitoringData() {
        var opMonitoringData = new OpMonitoringData(CLIENT, 100);
        var globalConfProvider = mock(GlobalConfProvider.class);
        var serverConfProvider = mock(ServerConfProvider.class);
        var processor = createProcessor(globalConfProvider, serverConfProvider);

        RequestWrapper request = RequestWrapper.of(getMockedRequest());
        var respWrapper = mock(ResponseWrapper.class);
        var ctx = new RestRequestContext(request, respWrapper, opMonitoringData);

        assertThrows(XrdRuntimeException.class, () -> processor.process(ctx));

        verifyOpMonitoringData(opMonitoringData.getData());
    }

    private ClientRestMessageProcessor createProcessor(GlobalConfProvider globalConfProvider,
                                                       ServerConfProvider serverConfProvider) {
        var proxyProperties = ConfigUtils.defaultConfiguration(ProxyProperties.class);
        var commonProperties = ConfigUtils.defaultConfiguration(CommonProperties.class);
        var opMonitoringDataHelper = new OpMonitoringDataHelper(globalConfProvider, serverConfProvider);
        var httpSenderProvider = mock(HttpSenderProvider.class);
        var messageSigningService = mock(MessageSigningService.class);
        var clientVerificationService = mock(ClientVerificationService.class);
        var clientRequestPreparationService = mock(ClientRequestPreparationService.class);
        when(httpSenderProvider.createClientHttpSender()).thenReturn(mock(HttpSender.class));
        when(clientRequestPreparationService.prepareRequest(any(), any(), any(), any(), any(), any()))
                .thenThrow(XrdRuntimeException.systemException(UNKNOWN_MEMBER, "No address found"));

        return new ClientRestMessageProcessor(
                messageSigningService,
                httpSenderProvider,
                clientVerificationService,
                opMonitoringDataHelper,
                globalConfProvider,
                proxyProperties,
                commonProperties,
                mock(OcspVerifierFactory.class),
                clientRequestPreparationService,
                mock(DspRequestProcessor.class),
                mock(IdentifierValidationService.class)
        );
    }

    @Test
    void isManagementRequestReturnsTrueWhenServiceIdMatchesManagementSubsystem() {
        var management = ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(management);
        var serviceId = ServiceId.Conf.create(management, "clientReg");

        var processor = createProcessor(globalConfProvider, mock(ServerConfProvider.class));

        assertThat(processor.isManagementRequest(serviceId)).isTrue();
    }

    @Test
    void isManagementRequestReturnsFalseForNonManagementClient() {
        var management = ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
        var other = ClientId.Conf.create("DEV", "COM", "4321", "TestClient");
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(management);
        var serviceId = ServiceId.Conf.create(other, "mock1");

        var processor = createProcessor(globalConfProvider, mock(ServerConfProvider.class));

        assertThat(processor.isManagementRequest(serviceId)).isFalse();
    }

    @Test
    void isManagementRequestReturnsFalseWhenGlobalConfManagementServiceIsNull() {
        var other = ClientId.Conf.create("DEV", "COM", "4321", "TestClient");
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var serviceId = ServiceId.Conf.create(other, "mock1");

        var processor = createProcessor(globalConfProvider, mock(ServerConfProvider.class));

        assertThat(processor.isManagementRequest(serviceId)).isFalse();
    }

    @Test
    void managementServiceIdProducesDspRequestWithManagementFlagTrue() {
        var management = ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(management);
        var serviceId = ServiceId.Conf.create(management, "clientReg");

        var processor = createProcessor(globalConfProvider, mock(ServerConfProvider.class));

        // isManagementRequest drives the management flag in the DspRequest constructed by sendRequest.
        var dspRequest = new DspRequest(serviceId, null, processor.isManagementRequest(serviceId));
        assertThat(dspRequest.management()).isTrue();
    }

    @Test
    void nonManagementServiceIdProducesDspRequestWithManagementFlagFalse() {
        var management = ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
        var other = ClientId.Conf.create("DEV", "COM", "4321", "TestClient");
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(management);
        var serviceId = ServiceId.Conf.create(other, "testService");

        var processor = createProcessor(globalConfProvider, mock(ServerConfProvider.class));

        var dspRequest = new DspRequest(serviceId, null, processor.isManagementRequest(serviceId));
        assertThat(dspRequest.management()).isFalse();
    }

    private void verifyOpMonitoringData(Map<String, Object> data) {
        assertEquals("Client", data.get("securityServerType"));
        assertEquals("REST", data.get("serviceType"));
        assertEquals("pets", data.get("serviceCode"));
        assertEquals("GET", data.get("restMethod"));
        assertNull(data.get("restPath"));
        assertEquals(Version.XROAD_VERSION, data.get("xRoadVersion"));
        assertNotNull(data.get("clientXRoadInstance"), "DEV");
        assertEquals("1234", data.get("clientMemberCode"));
        assertEquals("TestService", data.get("clientSubsystemCode"));
        assertEquals("DEV", data.get("serviceXRoadInstance"));
        assertEquals("1234", data.get("serviceMemberCode"));
        assertEquals("TestService", data.get("serviceSubsystemCode"));
        assertEquals("1", data.get("messageProtocolVersion"));
        assertNotNull(data.get("xRequestId"));
        assertEquals(false, data.get("succeeded"));
    }

    private Request getMockedRequest() {
        final var request = mock(Request.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHttpURI()).thenReturn(HttpURI.build(URI.create("http://localhost:4210/r1/DEV/COM/1234/TestService/pets/pets/1")));
        var clientId = new PreEncodedHttpField(HEADER_CLIENT_ID, "DEV/COM/1234/TestService");
        when(request.getHeaders()).thenReturn(HttpFields.from(clientId));
        return request;
    }
}
