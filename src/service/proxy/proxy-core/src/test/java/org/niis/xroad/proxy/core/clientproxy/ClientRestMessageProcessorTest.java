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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.SneakyThrows;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.server.Request;
import org.junit.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.IsAuthenticationData;

import java.net.URI;
import java.util.Map;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_CLIENT_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.CLIENT;
import static org.niis.xroad.serverconf.IsAuthentication.NOSSL;
import static org.niis.xroad.serverconf.model.Client.STATUS_REGISTERED;

public class ClientRestMessageProcessorTest {

    @SneakyThrows
    @Test
    public void processShouldAddOpMonitoringData() {
        var opMonitoringData = new OpMonitoringData(CLIENT, 100);
        var clientRestMessageProcessor = createMockedClientRestMessageProcessor(opMonitoringData);

        assertThrows(CodedException.class, clientRestMessageProcessor::process);

        verifyOpMonitoringData(opMonitoringData.getData());
    }

    @SneakyThrows
    private ClientRestMessageProcessor createMockedClientRestMessageProcessor(OpMonitoringData opMonitoringData) {
        var globalConfProvider = mock(GlobalConfProvider.class);
        var keyConfProvider = mock(KeyConfProvider.class);
        var serverConfProvider = mock(ServerConfProvider.class);
        var certChainFactory = mock(CertChainFactory.class);
        RequestWrapper request = RequestWrapper.of(getMockedRequest());
        var respWrapper = mock(ResponseWrapper.class);
        var httpClient = mock(HttpClient.class);
        var isAuthenticationData = mock(IsAuthenticationData.class);
        var commonBeanProxy =
                new CommonBeanProxy(globalConfProvider, serverConfProvider, keyConfProvider, null, certChainFactory, null);
        var clientRestMessageProcessor =
                new ClientRestMessageProcessor(commonBeanProxy, request, respWrapper, httpClient, isAuthenticationData, opMonitoringData);
        when(serverConfProvider.getMemberStatus(any())).thenReturn(STATUS_REGISTERED);
        when(serverConfProvider.getIsAuthentication(any())).thenReturn(NOSSL);
        when(serverConfProvider.getMaintenanceMode()).thenReturn(new ServerConfProvider.MaintenanceMode(false, null));
        return clientRestMessageProcessor;
    }

    private void verifyOpMonitoringData(Map<String, Object> data) {
        assertEquals("Client", data.get("securityServerType"));
        assertEquals("REST", data.get("serviceType"));
        assertEquals("pets", data.get("serviceCode"));
        assertEquals("GET", data.get("restMethod"));
        assertNull(data.get("restPath"));
        assertEquals(Version.XROAD_VERSION, data.get("xRoadVersion"));
        assertNotNull("DEV", data.get("clientXRoadInstance"));
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
