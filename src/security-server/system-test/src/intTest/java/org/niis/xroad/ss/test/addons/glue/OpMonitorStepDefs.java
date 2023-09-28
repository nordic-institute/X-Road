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
package org.niis.xroad.ss.test.addons.glue;

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.JaxbUtils;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParser;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.opmonitordaemon.message.GetSecurityServerHealthDataResponseType;
import ee.ria.xroad.opmonitordaemon.message.ServiceEventsType;

import io.cucumber.java.en.Step;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.niis.xroad.ss.test.addons.api.FeignXRoadSoapRequestsApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.w3c.dom.Element;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.niis.xroad.ss.test.addons.glue.BaseStepDefs.StepDataKey.XROAD_SOAP_RESPONSE;

public class OpMonitorStepDefs extends BaseStepDefs {
    @Autowired
    private FeignXRoadSoapRequestsApi xRoadSoapRequestsApi;

    private static final String OPERATIONAL_DATA_REQUEST = "src/intTest/resources/files/soap-requests/operational-data.request";
    private static final String HEALTH_DATA_REQUEST = "src/intTest/resources/files/soap-requests/health-data.request";
    private static final String OPERATIONAL_DATA_JSON = "operational-monitoring-data.json.gz";
    private static final String OP_MONITORING_XSD = "http://x-road.eu/xsd/op-monitoring.xsd";
    private static final long SERVICES_EVENTS_COUNT = 2;
    private static final long HEALTH_STATISTICS_PERIOD_SECONDS = 600;


    @SuppressWarnings("checkstyle:OperatorWrap")
    @Step("Security Server Operational Data request was sent")
    public void executeGetSecurityServerOperationalData() throws Exception {
        InputStream is = new FileInputStream(OPERATIONAL_DATA_REQUEST);
        SoapParser parser = new SoapParserImpl();
        SoapMessageImpl request = (SoapMessageImpl) parser.parse(MimeTypes.TEXT_XML_UTF8, is);
        ResponseEntity<String> response = xRoadSoapRequestsApi.getXRoadSoapResponse(request.getBytes());
        putStepData(XROAD_SOAP_RESPONSE, response);
    }

    @Step("Security Server Health Data request was sent")
    public void executeGetSecurityServerHealthData() throws Exception {
        InputStream is = new FileInputStream(HEALTH_DATA_REQUEST);
        SoapParser parser = new SoapParserImpl();
        SoapMessageImpl request = (SoapMessageImpl) parser.parse(MimeTypes.TEXT_XML_UTF8, is);
        ResponseEntity<String> response = xRoadSoapRequestsApi.getXRoadSoapResponse(request.getBytes());
        putStepData(XROAD_SOAP_RESPONSE, response);
    }

    @Step("Valid Security Server Health Data response is returned")
    public void validHealthDataResponseIsReturned() throws Exception {
        SoapParser parser = new SoapParserImpl();
        ResponseEntity<String> response = (ResponseEntity<String>) getStepData(XROAD_SOAP_RESPONSE).orElseThrow();
        SoapMessageImpl soupMessage = (SoapMessageImpl) parser.parse(MimeTypes.TEXT_XML, IOUtils.toInputStream(response.getBody(), UTF_8));
        GetSecurityServerHealthDataResponseType responseData =
                JaxbUtils.createUnmarshaller(
                                GetSecurityServerHealthDataResponseType.class)
                        .unmarshal(SoapUtils.getFirstChild(
                                        soupMessage.getSoap().getSOAPBody()),
                                GetSecurityServerHealthDataResponseType.class)
                        .getValue();

        assertEquals(HEALTH_STATISTICS_PERIOD_SECONDS, responseData.getStatisticsPeriodSeconds());
        assertEquals(SERVICES_EVENTS_COUNT, responseData.getServicesEvents()
                .getServiceEvents().size());
        var serviceId = ServiceId.Conf.create("CS", "GOV", "0245437-2",
                null, "getSecurityServerOperationalData", null);
        assertEquals(serviceId, getService(responseData.getServicesEvents().getServiceEvents(), serviceId));
    }

    private ServiceId.Conf getService(List<ServiceEventsType> services, ServiceId.Conf serviceId) {
        return services.stream()
                .filter(s -> s.getService().equals(serviceId))
                .findFirst()
                .map(ServiceEventsType::getService)
                .orElseThrow();
    }

    @Step("Valid Security Server Operational data response is returned")
    public void validOperationalDataIsReturned() throws Exception {
        ResponseEntity<String> response = (ResponseEntity<String>) getStepData(XROAD_SOAP_RESPONSE).orElseThrow();
        SoapMessageDecoder decoder = new SoapMessageDecoder(Objects.requireNonNull(response.getHeaders().getContentType()).toString(),
                new SoapMessageDecoder.Callback() {

                    @Override
                    public void soap(SoapMessage message, Map<String, String> headers) {
                        assertEquals("cid:" + OPERATIONAL_DATA_JSON, findRecordsContentId(message, "records"));
                        assertNotEquals("0", findRecordsContentId(message, "recordsCount"));
                    }

                    @Override
                    public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders) {
                        String expectedCid = "<" + OPERATIONAL_DATA_JSON + ">";
                        assertEquals(expectedCid, additionalHeaders.get("content-id"));
                    }

                    @Override
                    public void fault(SoapFault fault) {
                    }

                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Exception t) {
                    }
                });

        decoder.parse(IOUtils.toInputStream(Objects.requireNonNull(response.getBody()), UTF_8));
    }

    @SneakyThrows
    private static String findRecordsContentId(SoapMessage message, String elementTagName) {
        Element response = (Element) message.getSoap().getSOAPBody()
                .getElementsByTagNameNS(OP_MONITORING_XSD, "getSecurityServerOperationalDataResponse")
                .item(0);
        return response.getElementsByTagNameNS(OP_MONITORING_XSD, elementTagName)
                .item(0)
                .getTextContent();
    }
}
