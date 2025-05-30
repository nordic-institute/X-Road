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
package org.niis.xroad.proxy.core.testsuite.testcases;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessageImpl;

import com.google.protobuf.util.Timestamps;
import io.grpc.stub.StreamObserver;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.soap.SOAPBody;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.monitor.common.HistogramMetrics;
import org.niis.xroad.monitor.common.Metrics;
import org.niis.xroad.monitor.common.MetricsGroup;
import org.niis.xroad.monitor.common.MetricsServiceGrpc;
import org.niis.xroad.monitor.common.SystemMetricsReq;
import org.niis.xroad.monitor.common.SystemMetricsResp;
import org.niis.xroad.proxy.core.test.Message;
import org.niis.xroad.proxy.core.test.MessageTestCase;
import org.niis.xroad.proxy.core.test.MetaserviceTestUtil;
import org.niis.xroad.proxy.core.test.TestSuiteGlobalConf;
import org.niis.xroad.proxy.core.test.TestSuiteServerConf;
import org.niis.xroad.proxymonitor.message.GetSecurityServerMetricsResponse;
import org.niis.xroad.proxymonitor.message.HistogramMetricType;
import org.niis.xroad.proxymonitor.message.MetricSetType;
import org.niis.xroad.proxymonitor.message.MetricType;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.core.Is.is;
import static org.niis.xroad.proxy.core.test.MetaserviceTestUtil.verifyAndGetSingleBodyElementOfType;

/**
 * Test member list retrieval
 * Result: client receives a list of members.
 */
@Slf4j
public class SecurityServerMetricsMessage extends MessageTestCase {
    private static final String EXPECTED_XR_INSTANCE = "EE";
    private static final ClientId.Conf DEFAULT_OWNER_CLIENT = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "BUSINESS", "producer");

    private static final SecurityServerId.Conf DEFAULT_OWNER_SERVER =
            SecurityServerId.Conf.create(DEFAULT_OWNER_CLIENT, "ownerServer");

    private static final String EXPECTED_METRIC_SET_NAME = "someMetricSet";
    private static final double MIN_VALUE = 0.125;
    private static final BigDecimal EXPECTED_RESPONSE_MIN_VALUE = BigDecimal.valueOf(MIN_VALUE);
    private static final double MAX_VALUE = 500.143;
    private static final BigDecimal EXPECTED_RESPONSE_MAX_VALUE = BigDecimal.valueOf(MAX_VALUE);

    private static Unmarshaller unmarshaller;

    private static RpcServer monitorRpcServer;

    /**
     * Constructs the test case.
     */
    public SecurityServerMetricsMessage() {
        this.requestFileName = "getMetrics.query";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {

        SoapMessageImpl soap = (SoapMessageImpl) receivedResponse.parse().getSoap();
        SOAPBody body = soap.getSoap().getSOAPBody();

        // the content type might arrive without the space,
        // even though the MetadataServiceHandler uses the same MimeUtils value
        List<String> expectedContentTypes = MetaserviceTestUtil.xmlUtf8ContentTypes();

        assertThat("Wrong content type", receivedResponse.getContentType(), isIn(expectedContentTypes));

        final MetricSetType rootSet =
                verifyAndGetSingleBodyElementOfType(body,
                        GetSecurityServerMetricsResponse.class,
                        () -> unmarshaller).getMetricSet();

        assertThat("Wrong root name", rootSet.getName(), is(DEFAULT_OWNER_SERVER.toString()));
        assertThat("Wrong amount of received metrics", rootSet.getMetrics().size(), is(2));

        final MetricType proxyVersionMetric = rootSet.getMetrics().get(0);

        assertThat("Missing proxy version from response", proxyVersionMetric.getName(),
                equalTo("proxyVersion"));

        final MetricType containingMetric = rootSet.getMetrics().get(1);

        assertThat("Wrong name on metric set", containingMetric.getName(), equalTo(EXPECTED_METRIC_SET_NAME));

        assertThat("Was expecting a set of data", containingMetric, instanceOf(MetricSetType.class));
        MetricSetType metricSet = (MetricSetType) containingMetric;


        HistogramMetricType histogram = (HistogramMetricType) metricSet.getMetrics().get(0);
        assertThat("Wrong min value", histogram.getMin(), is(EXPECTED_RESPONSE_MIN_VALUE));
        assertThat("Wrong max value", histogram.getMax(), is(EXPECTED_RESPONSE_MAX_VALUE));
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();

        monitorRpcServer = RpcServer.newServer(
                SystemProperties.getGrpcInternalHost(),
                SystemProperties.getEnvMonitorPort(),
                builder -> builder.addService(new MockMetricsProvider()));
        monitorRpcServer.afterPropertiesSet();

        globalConfProvider.setGlobalConfProvider(new TestSuiteGlobalConf() {
            @Override
            public String getInstanceIdentifier() {
                return EXPECTED_XR_INSTANCE;
            }
        });
        serverConfProvider.setServerConfProvider(new TestSuiteServerConf() {
            @Override
            public SecurityServerId.Conf getIdentifier() {
                return DEFAULT_OWNER_SERVER;
            }
        });

        unmarshaller = JAXBContext.newInstance(GetSecurityServerMetricsResponse.class).createUnmarshaller();
    }

    @Override
    protected void closeDown() throws Exception {
        monitorRpcServer.destroy();
    }

    private static SystemMetricsResp createMetricsResponse() {
        var histogram = HistogramMetrics.newBuilder()
                .setName("exampleHistogram")
                .setUpdateDateTime(Timestamps.now())
                .setDistribution75ThPercentile(75)
                .setDistribution95ThPercentile(95)
                .setDistribution98ThPercentile(98)
                .setDistribution99ThPercentile(99)
                .setDistribution999ThPercentile(99.9)
                .setMax(MAX_VALUE)
                .setMean(50)
                .setMedian(51)
                .setMin(MIN_VALUE)
                .setStdDev(2);

        return SystemMetricsResp.newBuilder()
                .setMetrics(MetricsGroup.newBuilder()
                        .setName(EXPECTED_METRIC_SET_NAME)
                        .addMetrics(Metrics.newBuilder().setSingleHistogram(histogram)))
                .build();
    }

    /**
     * Mock provider for metrics data
     */
    public static class MockMetricsProvider extends MetricsServiceGrpc.MetricsServiceImplBase {

        @Override
        public void getMetrics(SystemMetricsReq request, StreamObserver<SystemMetricsResp> responseObserver) {
            responseObserver.onNext(createMetricsResponse());
            responseObserver.onCompleted();
        }
    }
}
