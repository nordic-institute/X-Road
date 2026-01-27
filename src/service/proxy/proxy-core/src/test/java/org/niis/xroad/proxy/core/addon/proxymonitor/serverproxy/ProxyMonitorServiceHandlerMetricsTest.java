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
package org.niis.xroad.proxy.core.addon.proxymonitor.serverproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.RequestWrapper;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.monitor.common.Metrics;
import org.niis.xroad.monitor.common.MetricsGroup;
import org.niis.xroad.monitor.common.SingleMetrics;
import org.niis.xroad.monitor.common.SystemMetricsResp;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.test.ProxyTestSuiteHelper;
import org.niis.xroad.proxy.core.test.TestSuiteGlobalConf;
import org.niis.xroad.proxy.core.test.TestSuiteServerConf;
import org.niis.xroad.proxymonitor.message.GetSecurityServerMetricsResponse;
import org.niis.xroad.proxymonitor.message.MetricSetType;
import org.niis.xroad.proxymonitor.message.MetricType;
import org.niis.xroad.proxymonitor.message.StringMetricType;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.message.SoapMessageTestUtil.build;
import static ee.ria.xroad.common.util.MimeTypes.TEXT_XML_UTF8;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.proxy.core.test.MetaserviceTestUtil.verifyAndGetSingleBodyElementOfType;

/**
 * Unit tests for {@link ProxyMonitorServiceHandlerImpl}
 */
public class ProxyMonitorServiceHandlerMetricsTest {

    private static final String EXPECTED_XR_INSTANCE = "EE";
    private static final ClientId.Conf DEFAULT_OWNER_CLIENT = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "GOV",
            "1234TEST_CLIENT");

    private static final SecurityServerId.Conf DEFAULT_OWNER_SERVER =
            SecurityServerId.Conf.create(DEFAULT_OWNER_CLIENT, "ownerServer");

    private static final ServiceId.Conf MONITOR_SERVICE_ID = ServiceId.Conf.create(DEFAULT_OWNER_CLIENT,
            ProxyMonitorServiceHandlerImpl.SERVICE_CODE);

    private static Unmarshaller unmarshaller;
    private static MessageFactory messageFactory;

    private ServerConfProvider serverConfProvider;
    private GlobalConfProvider globalConfProvider;
    private RequestWrapper mockRequest;
    private ProxyMessage mockProxyMessage;

    private final MonitorRpcClient mockMonitorClient = mock(MonitorRpcClient.class);

    /**
     * Init class-wide test instances
     */
    @BeforeClass
    public static void initCommon() throws JAXBException, SOAPException {
        unmarshaller = JAXBContext.newInstance(ObjectFactory.class, SoapHeader.class,
                        GetSecurityServerMetricsResponse.class)
                .createUnmarshaller();
        messageFactory = MessageFactory.newInstance();
    }

    /**
     * Init data for tests
     */
    @Before
    public void init() throws IOException {
        var proxyTestSuiteHelper = new ProxyTestSuiteHelper();
        serverConfProvider = new TestSuiteServerConf(proxyTestSuiteHelper) {
            @Override
            public SecurityServerId.Conf getIdentifier() {
                return DEFAULT_OWNER_SERVER;
            }
        };
        globalConfProvider = new TestSuiteGlobalConf(proxyTestSuiteHelper);
        mockRequest = mock(RequestWrapper.class);
        mockProxyMessage = mock(ProxyMessage.class);

        when(mockProxyMessage.getSoapContentType()).thenReturn(MimeTypes.TEXT_XML_UTF8);
    }

    @Test
    public void startHandingShouldProduceAllMetrics() throws Exception {

        // setup
        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl(serverConfProvider, globalConfProvider,
                mockMonitorClient);

        final SoapMessageImpl soapMessage = build(DEFAULT_OWNER_CLIENT, MONITOR_SERVICE_ID,
                "testUser", randomUUID().toString());

        when(mockProxyMessage.getSoap()).thenReturn(soapMessage);
        doAnswer(copyStream(new ByteArrayInputStream(soapMessage.getXml().getBytes(soapMessage.getCharset()))))
                .when(mockProxyMessage)
                .writeSoapContent(any());

        handlerToTest.canHandle(MONITOR_SERVICE_ID, mockProxyMessage);

        final String expectedMetricsSetName = "someName";
        final String expectedMetricName = "metricName123-23";
        final String expectedMetricValue = "123SomeValue";

        SystemMetricsResp resp = SystemMetricsResp.newBuilder().setMetrics(
                MetricsGroup.newBuilder()
                        .setName(expectedMetricsSetName)
                        .addMetrics(Metrics.newBuilder().setSingleMetrics(SingleMetrics.newBuilder()
                                .setName(expectedMetricName)
                                .setValue(expectedMetricValue)
                                .build()).build())
                        .build()).build();

        when(mockMonitorClient.getMetrics(anyList(), anyBoolean())).thenReturn(resp.getMetrics());

        // execution
        handlerToTest.startHandling(mockRequest, mockProxyMessage, mock(OpMonitoringData.class));

        //verification
        assertThat("Wrong content type", handlerToTest.getResponseContentType(), is(TEXT_XML_UTF8));

        final SOAPMessage message = messageFactory.createMessage(null, handlerToTest.getResponseContent());

        final SoapHeader xrHeader = unmarshaller.unmarshal(message.getSOAPHeader(), SoapHeader.class).getValue();

        assertThat("Response client does not match", xrHeader.getClient(), is(DEFAULT_OWNER_CLIENT));
        assertThat("Response client does not match", xrHeader.getService(), is(MONITOR_SERVICE_ID));

        final MetricSetType root = verifyAndGetSingleBodyElementOfType(message.getSOAPBody(),
                GetSecurityServerMetricsResponse.class, () -> unmarshaller).getMetricSet();
        // root metrics should have the security server id as name
        assertThat("Metrics set name does not match", root.getName(), is(DEFAULT_OWNER_SERVER.toString()));

        final List<MetricType> responseMetrics = root.getMetrics();
        assertThat("Missing proxy version from metrics", responseMetrics,
                hasItem(hasProperty("name", is("proxyVersion"))));

        assertThat("Missing the expected metrics set",
                responseMetrics, hasItem(instanceOf(MetricSetType.class)));

        final MetricSetType responseDataMetrics = (MetricSetType) responseMetrics.stream() // we just asserted this..
                .filter(m -> m instanceof MetricSetType).findFirst().orElseThrow(IllegalStateException::new);

        assertThat(responseDataMetrics.getName(), is(expectedMetricsSetName));
        assertThat(responseDataMetrics.getMetrics().size(), is(1));

        final StringMetricType responseMetric = (StringMetricType) responseDataMetrics.getMetrics().getFirst();
        assertThat("Wrong metric name", responseMetric.getName(), is(expectedMetricName));
        assertThat("Wrong metric value", responseMetric.getValue(), is(expectedMetricValue));
    }

    /**
     * As above but only environmental parameters defined in outputSpec.
     *
     * @throws Exception
     */
    @Test
    public void startHandingShouldProduceRequestedMetrics() throws Exception {

        // setup
        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl(serverConfProvider, globalConfProvider,
                mockMonitorClient);
        final SoapMessageImpl soapMessage = build(
                false,
                DEFAULT_OWNER_CLIENT,
                MONITOR_SERVICE_ID,
                "testUser",
                randomUUID().toString(),
                new MetricsQueryBuilder(Arrays.asList("proxyVersion", "CommittedVirtualMemory", "DiskSpaceFree")));

        when(mockProxyMessage.getSoap()).thenReturn(soapMessage);
        doAnswer(copyStream(new ByteArrayInputStream(soapMessage.getXml().getBytes(soapMessage.getCharset()))))
                .when(mockProxyMessage)
                .writeSoapContent(any());

        handlerToTest.canHandle(MONITOR_SERVICE_ID, mockProxyMessage);

        final String expectedMetricsSetName = "someName";
        final String expectedMetricName = "metricName123-23";
        final String expectedMetricValue = "123SomeValue";

        SystemMetricsResp resp = SystemMetricsResp.newBuilder().setMetrics(
                MetricsGroup.newBuilder()
                        .setName(expectedMetricsSetName)
                        .addMetrics(Metrics.newBuilder().setSingleMetrics(SingleMetrics.newBuilder()
                                .setName(expectedMetricName)
                                .setValue(expectedMetricValue)
                                .build()).build())
                        .build()).build();

        when(mockMonitorClient.getMetrics(anyList(),
                anyBoolean())).thenReturn(resp.getMetrics());

        // execution
        handlerToTest.startHandling(mockRequest, mockProxyMessage, mock(OpMonitoringData.class));

        //verification
        assertThat("Wrong content type", handlerToTest.getResponseContentType(), is(TEXT_XML_UTF8));

        final SOAPMessage message = messageFactory.createMessage(null, handlerToTest.getResponseContent());

        final SoapHeader xrHeader = unmarshaller.unmarshal(message.getSOAPHeader(), SoapHeader.class).getValue();

        assertThat("Response client does not match", xrHeader.getClient(), is(DEFAULT_OWNER_CLIENT));
        assertThat("Response client does not match", xrHeader.getService(), is(MONITOR_SERVICE_ID));

        final MetricSetType root = verifyAndGetSingleBodyElementOfType(message.getSOAPBody(),
                GetSecurityServerMetricsResponse.class, () -> unmarshaller).getMetricSet();
        // root metrics should have the security server id as name
        assertThat("Metrics set name does not match", root.getName(), is(DEFAULT_OWNER_SERVER.toString()));

        final List<MetricType> responseMetrics = root.getMetrics();
        assertThat("Missing proxy version from metrics", responseMetrics,
                hasItem(hasProperty("name", is("proxyVersion"))));

        assertThat("Missing the expected metrics set",
                responseMetrics, hasItem(instanceOf(MetricSetType.class)));

        final MetricSetType responseDataMetrics = (MetricSetType) responseMetrics.stream() // we just asserted this..
                .filter(m -> m instanceof MetricSetType).findFirst().orElseThrow(IllegalStateException::new);

        assertThat(responseDataMetrics.getName(), is(expectedMetricsSetName));
        assertThat(responseDataMetrics.getMetrics().size(), is(1));

        final StringMetricType responseMetric = (StringMetricType) responseDataMetrics.getMetrics().getFirst();
        assertThat("Wrong metric name", responseMetric.getName(), is(expectedMetricName));
        assertThat("Wrong metric value", responseMetric.getValue(), is(expectedMetricValue));
    }

    private Answer<Object> copyStream(InputStream source) {
        return args -> {
            OutputStream out = args.getArgument(0);
            IOUtils.copy(source, out);
            return null;
        };
    }
}
