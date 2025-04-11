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

import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParser;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.opmonitordaemon.OperationalDataRecord;
import ee.ria.xroad.opmonitordaemon.OperationalDataRecords;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Step;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.niis.xroad.ss.test.addons.api.FeignXRoadRestRequestsApi;
import org.niis.xroad.ss.test.addons.api.FeignXRoadSoapRequestsApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.niis.xroad.ss.test.addons.glue.BaseStepDefs.StepDataKey.XROAD_SOAP_RESPONSE;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Slf4j
public class OpMonitorStepDefs extends BaseStepDefs {
    @Autowired
    private FeignXRoadSoapRequestsApi xRoadSoapRequestsApi;
    @Autowired
    private FeignXRoadRestRequestsApi xRoadRestRequestsApi;

    private static final String OPERATIONAL_DATA_REQUEST = "src/intTest/resources/files/soap-requests/operational-data.request";
    private static final String HEALTH_DATA_REQUEST = "src/intTest/resources/files/soap-requests/health-data.request";
    private static final String OPERATIONAL_DATA_JSON = "operational-monitoring-data.json.gz";
    private static final String OP_MONITORING_XSD = "http://x-road.eu/xsd/op-monitoring.xsd";

    @SuppressWarnings({"checkstyle:OperatorWrap", "checkstyle:MagicNumber"})
    @Step("Security Server Operational Data request was sent")
    public void executeGetSecurityServerOperationalData() throws Exception {
        Thread.sleep(2000); // make sure that messages are processed
        InputStream is = new FileInputStream(OPERATIONAL_DATA_REQUEST);
        SoapParser parser = new SoapParserImpl();
        SoapMessageImpl request = (SoapMessageImpl) parser.parse(MimeTypes.TEXT_XML_UTF8, is);
        ResponseEntity<byte[]> response = xRoadSoapRequestsApi.getXRoadSoapResponseAsBytes(request.getBytes());
        putStepData(XROAD_SOAP_RESPONSE, response);
    }

    @Step("Security Server saved endpoint REST method was sent for client {string}")
    public void executeSavedGetPets(String xRoadClientId) {
        try {
            xRoadRestRequestsApi.s3c2(xRoadClientId);
        } catch (Exception e) {
            log.info("There was error in saved endpoint REST call: {}", e.getMessage());
        }
    }

    @Step("Security Server saved endpoint OPENAPI3 method was sent for client {string}")
    public void executeSavedGetTest(String xRoadClientId) {
        try {
            xRoadRestRequestsApi.testOas31(xRoadClientId);
        } catch (Exception e) {
            log.info("There was error in saved endpoint OPENAPI3 call: {}", e.getMessage());
        }
    }

    @Step("Security Server not saved endpoint OPENAPI3 method was sent for client {string}")
    public void executeNotSavedGetTest(String xRoadClientId) {
        try {
            xRoadRestRequestsApi.s4c2(xRoadClientId);
        } catch (Exception e) {
            log.info("There was error in not saved endpoint OPENAPI3 call: {}", e.getMessage());
        }
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
        @SuppressWarnings("unchecked")
        ResponseEntity<String> response = (ResponseEntity<String>) getStepData(XROAD_SOAP_RESPONSE).orElseThrow();
        validate(response).assertion(equalsStatusCodeAssertion(HttpStatus.OK)).execute();

        SoapParser parser = new SoapParserImpl();
        SoapMessageImpl soupMessage = (SoapMessageImpl) parser
                .parse(MimeTypes.TEXT_XML, IOUtils.toInputStream(Objects.requireNonNull(response.getBody()), UTF_8));

        assertEquals("600", findHealthDataRecordsContentId(soupMessage.getSoap(), "statisticsPeriodSeconds"));
        assertEquals("1", findHealthDataRecordsContentId(soupMessage.getSoap(), "successfulRequestCount"));
    }

    @Step("Valid Security Server Operational data response is returned")
    public void validOperationalDataIsReturned() throws Exception {
        @SuppressWarnings("unchecked")
        ResponseEntity<byte[]> response = (ResponseEntity<byte[]>) getStepData(XROAD_SOAP_RESPONSE).orElseThrow();
        validate(response).assertion(equalsStatusCodeAssertion(HttpStatus.OK)).execute();

        SoapMessageDecoder decoder = new SoapMessageDecoder(Objects.requireNonNull(response.getHeaders().getContentType()).toString(),
                new SoapMessageDecoder.Callback() {

                    @Override
                    @SuppressWarnings("checkstyle:MagicNumber")
                    public void soap(SoapMessage message, Map<String, String> headers) {
                        assertEquals("cid:" + OPERATIONAL_DATA_JSON, findOperationalDataRecordsContentId(message, "records"));
                        var recordsCount = Integer.parseInt(findOperationalDataRecordsContentId(message, "recordsCount"));
                        log.info("operational data records count: {}", recordsCount);
                        assertTrue(recordsCount > 0);
                    }


                    @Override
                    public void attachment(String contentType, InputStream content,
                                           Map<String, String> additionalHeaders) throws IOException {

                        String expectedCid = "<" + OPERATIONAL_DATA_JSON + ">";
                        assertEquals(expectedCid, additionalHeaders.get("content-id"));

                        OperationalDataRecords monitoringData = new ObjectMapper()
                                .readValue(readGzipContent(content), OperationalDataRecords.class);

                        // saved REST endpoint call: client path is null and producer path is saved path
                        verifyRestRecord(getClientRecord(monitoringData, "REST", "s3c2"), null);
                        verifyRestRecord(getProducerRecord(monitoringData, "REST", "s3c2"), "/*/pets/*");
                        // saved OPENAPI endpoint call: client path is null and producer path is saved path
                        verifyRestRecord(getClientRecord(monitoringData, "OPENAPI3", "testOas31"), null);
                        verifyRestRecord(getProducerRecord(monitoringData, "OPENAPI3", "testOas31"), "/test");
                        // not saved OPENAPI endpoint call: both client and producer path are null
                        verifyRestRecord(getClientRecord(monitoringData, "OPENAPI3", "s4c2"), null);
                        verifyRestRecord(getProducerRecord(monitoringData, "OPENAPI3", "s4c2"), null);
                        // WSDL call: method and path values are null
                        verifyWsdlRecord(getProducerRecord(monitoringData, "WSDL", "clientDisable"));
                    }

                    @Override
                    public void fault(SoapFault fault) {
                        throw fault.toCodedException();
                    }

                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Exception t) throws Exception {
                        throw t;
                    }
                });

        decoder.parse(new ByteArrayInputStream(Objects.requireNonNull(response.getBody())));
    }

    private OperationalDataRecord getRecord(OperationalDataRecords monitoringData, String securityServerType,
                                            String serviceType, String serviceCode) {
        return monitoringData.getRecords().stream()
                .filter(record -> serviceType.equals(record.getServiceType())
                        && serviceCode.equals(record.getServiceCode())
                        && record.getSecurityServerType().equals(securityServerType))
                .toList().getFirst();
    }

    private OperationalDataRecord getClientRecord(OperationalDataRecords monitoringData, String serviceType, String serviceCode) {
        return getRecord(monitoringData, "Client", serviceType, serviceCode);
    }

    private OperationalDataRecord getProducerRecord(OperationalDataRecords monitoringData, String serviceType, String serviceCode) {
        return getRecord(monitoringData, "Producer", serviceType, serviceCode);
    }

    private void verifyRecord(OperationalDataRecord record) {
        assertEquals("DEV", record.getClientXRoadInstance());
        assertEquals("COM", record.getClientMemberClass());
        assertEquals("1234", record.getClientMemberCode());
        assertNull(record.getClientSubsystemCode());
        assertEquals("DEV", record.getServiceXRoadInstance());
        assertEquals("COM", record.getServiceMemberClass());
        assertEquals("1234", record.getServiceMemberCode());
        assertEquals("ss0", record.getServiceSecurityServerAddress());
        assertNotNull(record.getXRoadVersion());
    }

    private void verifyRestRecord(OperationalDataRecord record, String path) {
        assertEquals("1", record.getMessageProtocolVersion());
        assertEquals("GET", record.getRestMethod());
        assertEquals(path, record.getRestPath());
        assertEquals("TestService", record.getServiceSubsystemCode());
        verifyRecord(record);
    }

    private void verifyWsdlRecord(OperationalDataRecord record) {
        assertEquals("4.0", record.getMessageProtocolVersion());
        assertNull(record.getRestMethod());
        assertNull(record.getRestPath());
        assertEquals("MANAGEMENT", record.getServiceSubsystemCode());
        verifyRecord(record);
    }


    private static String readGzipContent(InputStream inputStream) throws IOException {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(inputStream.readAllBytes()));
                InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            StringBuilder stringBuilder = new StringBuilder();
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }

            return stringBuilder.toString();
        }
    }

    @SneakyThrows
    private static String findOperationalDataRecordsContentId(SoapMessage message, String elementTagName) {
        return findRecordsContentId(message.getSoap().getSOAPBody(), elementTagName, "getSecurityServerOperationalDataResponse");
    }

    @SneakyThrows
    private static String findHealthDataRecordsContentId(SOAPMessage message, String elementTagName) {
        return findRecordsContentId(message.getSOAPBody(), elementTagName, "getSecurityServerHealthDataResponse");
    }

    private static String findRecordsContentId(SOAPBody soupBody, String elementTagName, String methodName) {
        Element response = (Element) soupBody
                .getElementsByTagNameNS(OP_MONITORING_XSD, methodName)
                .item(0);
        return response.getElementsByTagNameNS(OP_MONITORING_XSD, elementTagName)
                .item(0)
                .getTextContent();
    }
}
