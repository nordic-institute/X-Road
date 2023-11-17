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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.JaxbUtils;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParser;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.opmonitordaemon.message.GetSecurityServerHealthDataResponseType;

import com.codahale.metrics.MetricRegistry;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.OBJECT_READER;
import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.formatFullOperationalDataAsJson;
import static org.junit.Assert.assertEquals;

/**
 * Tests for verifying query request handler behavior.
 */
public class QueryRequestHandlerTest {

    private static final String RECORDS = "records";
    private static final String OPERATIONAL_DATA_RESPONSE =
            "getSecurityServerOperationalDataResponse";

    private static final String OM_NS =
            "http://x-road.eu/xsd/op-monitoring.xsd";

    private static final String OPERATIONAL_DATA_REQUEST =
            "src/test/resources/operationaldata.request";
    private static final String HEALTH_DATA_REQUEST =
            "src/test/resources/healthdata.request";

    private static final String SERVICE_TYPE_REST = "REST";

    private static final long TEST_TIMESTAMP = System.currentTimeMillis();

    private String testContentType;

    /**
     * Ensure that an operational data response contains the required attachment
     * that is correctly referenced from within the SOAP body.
     */
    @Test
    public void handleOperationalDataRequest() throws Exception {
        InputStream is = new FileInputStream(OPERATIONAL_DATA_REQUEST);
        SoapParser parser = new SoapParserImpl();
        SoapMessageImpl request = (SoapMessageImpl) parser.parse(
                MimeTypes.TEXT_XML_UTF8, is);

        QueryRequestHandler handler = new OperationalDataRequestHandler() {
            @Override
            protected OperationalDataRecords getOperationalDataRecords(
                    ClientId filterByClient, long recordsFrom, long recordsTo,
                    ClientId filterByServiceProvider,
                    Set<String> outputFields) {
                return new OperationalDataRecords(Collections.emptyList());
            }

            @Override
            protected ClientId getClientForFilter(ClientId clientId,
                    SecurityServerId serverId) throws Exception {
                return null;
            }
        };

        OutputStream out = new ByteArrayOutputStream();

        handler.handle(request, out, ct -> testContentType = ct);

        String baseContentType = MimeUtils.getBaseContentType(testContentType);
        assertEquals(MimeTypes.MULTIPART_RELATED, baseContentType);

        SoapMessageDecoder decoder = new SoapMessageDecoder(testContentType,
                new SoapMessageDecoder.Callback() {

                    @Override
                    public void soap(SoapMessage message, Map<String, String> headers)
                            throws Exception {
                        assertEquals("cid:" + OperationalDataRequestHandler.CID,
                                findRecordsContentId(message));
                    }

                    @Override
                    public void attachment(String contentType, InputStream content,
                                           Map<String, String> additionalHeaders) throws Exception {
                        String expectedCid = "<" + OperationalDataRequestHandler.CID
                                + ">";
                        assertEquals(expectedCid, additionalHeaders.get("content-id"));
                    }

                    @Override
                    public void onCompleted() {
                        // Do nothing.
                    }

                    @Override
                    public void onError(Exception t) throws Exception {
                        throw t;
                    }

                    @Override
                    public void fault(SoapFault fault) throws Exception {
                        throw fault.toCodedException();
                    }
                });

        decoder.parse(IOUtils.toInputStream(out.toString()));
    }

    @SneakyThrows
    private static String findRecordsContentId(SoapMessage message) {
        Element response = (Element) message.getSoap().getSOAPBody()
                .getElementsByTagNameNS(OM_NS, OPERATIONAL_DATA_RESPONSE)
                .item(0);
        return response.getElementsByTagNameNS(OM_NS, RECORDS).item(0)
                .getTextContent();
    }

    /**
     * Ensure that an health data response data is filtered and body is
     * constructed correctly.
     */
    @Test
    public void handleHealthDataRequest() throws Exception {
        InputStream is = new FileInputStream(HEALTH_DATA_REQUEST);
        SoapParser parser = new SoapParserImpl();
        SoapMessageImpl request = (SoapMessageImpl) parser.parse(
                MimeTypes.TEXT_XML_UTF8, is);

        QueryRequestHandler handler = new HealthDataRequestHandler(
                new TestMetricsRegistry());

        OutputStream out = new ByteArrayOutputStream();

        handler.handle(request, out, ct -> testContentType = ct);

        String baseContentType = MimeUtils.getBaseContentType(testContentType);
        assertEquals(MimeTypes.TEXT_XML, baseContentType);

        SoapMessageImpl response = (SoapMessageImpl) parser.parse(
                MimeTypes.TEXT_XML, IOUtils.toInputStream(out.toString()));

        GetSecurityServerHealthDataResponseType responseData =
                JaxbUtils.createUnmarshaller(
                        GetSecurityServerHealthDataResponseType.class)
                .unmarshal(SoapUtils.getFirstChild(
                        response.getSoap().getSOAPBody()),
                        GetSecurityServerHealthDataResponseType.class)
                .getValue();

        assertEquals(TEST_TIMESTAMP,
                responseData.getMonitoringStartupTimestamp());
        assertEquals(2, responseData.getServicesEvents()
                .getServiceEvents().size());
        assertEquals(ServiceId.Conf.create("XTEE-CI-XM", "GOV", "00000001",
                "System1", "xroad/GetRandom", "v2"),
                responseData.getServicesEvents().getServiceEvents()
                .get(0).getService());
        assertEquals(5, responseData.getServicesEvents()
                .getServiceEvents().get(0).getLastPeriodStatistics()
                .getSuccessfulRequestCount());
        assertEquals(5, responseData.getServicesEvents()
                .getServiceEvents().get(0).getLastPeriodStatistics()
                .getUnsuccessfulRequestCount());
    }

    private final class TestMetricsRegistry extends MetricRegistry {
        TestMetricsRegistry() throws IOException {
            HealthDataMetrics.registerInitialMetrics(this,
                    () -> TEST_TIMESTAMP);

            List<OperationalDataRecord> records = new ArrayList<>();

            ServiceId id = ServiceId.Conf.create("XTEE-CI-XM", "GOV",
                    "00000001", "System1", "xroad/GetRandom");

            for (int i = 0; i < 10; i++) {
                OperationalDataRecord record = createRecord(id, true);

                records.add(record);
            }

            id = ServiceId.Conf.create("XTEE-CI-XM", "GOV",
                    "00000001", "System2", "xroad/GetRandom");

            for (int i = 0; i < 10; i++) {
                records.add(createRecord(id, i % 2 == 0));
            }

            id = ServiceId.Conf.create("XTEE-CI-XM", "GOV",
                    "00000001", "System1", "xroad/GetRandom", "v2");

            for (int i = 0; i < 10; i++) {
                OperationalDataRecord record = createRecord(id, i % 2 == 0);

                records.add(record);
            }

            HealthDataMetrics.processRecords(this, records);
        }

        private OperationalDataRecord createRecord(ServiceId serviceId,
                boolean success) throws IOException {
            OperationalDataRecord record = OBJECT_READER.readValue(
                    formatFullOperationalDataAsJson(),
                    OperationalDataRecord.class);
            record.setServiceXRoadInstance(serviceId.getXRoadInstance());
            record.setServiceMemberClass(serviceId.getMemberClass());
            record.setServiceMemberCode(serviceId.getMemberCode());
            record.setServiceSubsystemCode(serviceId.getSubsystemCode());
            record.setServiceCode(serviceId.getServiceCode());
            record.setServiceVersion(serviceId.getServiceVersion());
            record.setSecurityServerType(
                    OpMonitoringData.SecurityServerType.PRODUCER
                    .getTypeString());
            record.setServiceType(SERVICE_TYPE_REST);
            record.setSucceeded(success);
            record.setRequestSize(999L);
            record.setResponseSize(888L);

            return record;
        }
    }

}
