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

package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RepresentedParty;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.MimeUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.security.cert.X509Certificate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpMonitoringDataHelperTest {

    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private SecurityServerId.Conf securityServerId;

    @InjectMocks
    private OpMonitoringDataHelper helper;

    @Test
    void testUpdateOpMonitoringClientSecurityServerAddress() {
        OpMonitoringData data = mock(OpMonitoringData.class);
        when(serverConfProvider.getIdentifier()).thenReturn(securityServerId);
        when(globalConfProvider.getSecurityServerAddress(securityServerId)).thenReturn("address");

        helper.updateOpMonitoringClientSecurityServerAddress(data);

        verify(data).setClientSecurityServerAddress("address");
    }

    @Test
    void testUpdateOpMonitoringServiceSecurityServerAddress() {
        OpMonitoringData data = mock(OpMonitoringData.class);
        when(serverConfProvider.getIdentifier()).thenReturn(securityServerId);
        when(globalConfProvider.getSecurityServerAddress(securityServerId)).thenReturn("address");

        helper.updateOpMonitoringServiceSecurityServerAddress(data);

        verify(data).setServiceSecurityServerAddress("address");
    }

    @Test
    void testUpdateOpMonitoringClientSecurityServerAddressWithCert() throws Exception {
        OpMonitoringData data = mock(OpMonitoringData.class);
        X509Certificate cert = mock(X509Certificate.class);
        when(globalConfProvider.getServerId(cert)).thenReturn(securityServerId);
        when(globalConfProvider.getSecurityServerAddress(securityServerId)).thenReturn("certAddress");

        helper.updateOpMonitoringClientSecurityServerAddress(data, cert);

        verify(data).setClientSecurityServerAddress("certAddress");
    }

    @Test
    void testUpdateOpMonitoringDataByRestRequest() {
        OpMonitoringData data = mock(OpMonitoringData.class);
        when(data.isProducer()).thenReturn(true);
        ClientId clientId = mock(ClientId.class);
        ServiceId serviceId = mock(ServiceId.class);
        RepresentedParty representedParty = new RepresentedParty("partyClass", "partyCode");
        RestRequest request = mock(RestRequest.class);
        when(request.getSender()).thenReturn(clientId);
        when(request.getServiceId()).thenReturn(serviceId);
        when(request.getQueryId()).thenReturn("queryId");
        when(request.findHeaderValueByName(MimeUtils.HEADER_USER_ID)).thenReturn("userId");
        when(request.findHeaderValueByName(MimeUtils.HEADER_ISSUE)).thenReturn("issue");
        when(request.getRepresentedParty()).thenReturn(representedParty);
        when(request.getVersion()).thenReturn(1);
        when(serverConfProvider.getDescriptionType(serviceId)).thenReturn(DescriptionType.REST);
        when(request.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(request.getServicePath()).thenReturn("/service/path");

        helper.updateOpMonitoringDataByRestRequest(data, request);

        verify(data).setClientId(clientId);
        verify(data).setServiceId(serviceId);
        verify(data).setMessageId("queryId");
        verify(data).setMessageUserId("userId");
        verify(data).setMessageIssue("issue");
        verify(data).setRepresentedParty(representedParty);
        verify(data).setMessageProtocolVersion("1");
        verify(data).setServiceType(DescriptionType.REST.name());
        verify(data).setRestMethod(RestRequest.Verb.GET.name());
        // we log rest path data only for PRODUCER
        verify(data).setRestPath("/service/path");
        verify(data).setXRoadVersion(Version.XROAD_VERSION);
    }

    @Test
    void testUpdateOpMonitoringDataBySoapMessage() {
        OpMonitoringData data = mock(OpMonitoringData.class);
        SoapMessageImpl soapMessage = mock(SoapMessageImpl.class);
        ClientId.Conf clientId = mock(ClientId.Conf.class);
        ServiceId.Conf serviceId = mock(ServiceId.Conf.class);
        RepresentedParty representedParty = mock(RepresentedParty.class);
        when(soapMessage.getClient()).thenReturn(clientId);
        when(soapMessage.getService()).thenReturn(serviceId);
        when(soapMessage.getQueryId()).thenReturn("soapQueryId");
        when(soapMessage.getUserId()).thenReturn("soapUserId");
        when(soapMessage.getIssue()).thenReturn("soapIssue");
        when(soapMessage.getRepresentedParty()).thenReturn(representedParty);
        when(soapMessage.getProtocolVersion()).thenReturn("1.0");
        when(soapMessage.getBytes()).thenReturn(new byte[10]);

        helper.updateOpMonitoringDataBySoapMessage(data, soapMessage);

        verify(data).setClientId(clientId);
        verify(data).setServiceId(serviceId);
        verify(data).setMessageId("soapQueryId");
        verify(data).setMessageUserId("soapUserId");
        verify(data).setMessageIssue("soapIssue");
        verify(data).setRepresentedParty(representedParty);
        verify(data).setMessageProtocolVersion("1.0");
        verify(data).setServiceType(DescriptionType.WSDL.name());
        verify(data).setRequestSize(10);
        verify(data).setXRoadVersion(Version.XROAD_VERSION);
    }
}
