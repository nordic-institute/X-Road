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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.ProtocolVersion;
import ee.ria.xroad.common.message.SoapBuilder;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.request.ObjectFactory;
import ee.ria.xroad.proxymonitor.message.GetSecurityServerMetricsType;
import ee.ria.xroad.proxymonitor.message.OutputSpecType;

import io.cucumber.java.en.Step;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import lombok.SneakyThrows;
import org.niis.xroad.ss.test.addons.api.FeignXRoadSoapRequestsApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.xml.namespace.QName;

import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.ss.test.addons.glue.BaseStepDefs.StepDataKey.XROAD_SOAP_RESPONSE;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ProxyMonitorStepDefs extends BaseStepDefs {

    @Autowired
    private FeignXRoadSoapRequestsApi xRoadSoapRequestsApi;



    private static final Marshaller MARSHALLER = createMarshaller();

    @SuppressWarnings("checkstyle:OperatorWrap")
    @Step("Security Server Metrics request was sent with queryId {string}")
    public void executeGetSecurityServerMetricsRequest(String queryId) {
        ResponseEntity<String> response = xRoadSoapRequestsApi.getXRoadSoapResponse(buildMetricsRequest(queryId, null)
                .getBytes());
        putStepData(XROAD_SOAP_RESPONSE, response);
    }

    @SuppressWarnings("checkstyle:OperatorWrap")
    @Step("Security Server Metric: {string} request was sent with queryId {string}")
    public void executeGetSecurityServerMetricsRequest(final String metricName, final String queryId) {
        ResponseEntity<String> response = xRoadSoapRequestsApi.getXRoadSoapResponse(buildMetricsRequest(queryId, metricName)
                .getBytes());
        putStepData(XROAD_SOAP_RESPONSE, response);
    }

    @Step("Valid Security Server Metrics response is returned")
    public void validMetricsResponseIsReturned() {
        ResponseEntity<String> response = (ResponseEntity<String>) getStepData(XROAD_SOAP_RESPONSE).orElseThrow();
        validate(response)
                .assertion(equalsStatusCodeAssertion(HttpStatus.OK))
                .assertion(xpath(response.getBody(),
                        "//monitoring:getSecurityServerMetricsResponse/monitoring:metricSet/monitoring:name",
                        "SERVER:CS/GOV/0245437-2/SS1"))
                .execute();
    }

    @Step("Valid numeric value returned for metric: {string}")
    public void validNumericMetricReturned(final String metricName) {
        ResponseEntity<String> response = (ResponseEntity<String>) getStepData(XROAD_SOAP_RESPONSE).orElseThrow();
        validate(response)
                .assertion(equalsStatusCodeAssertion(HttpStatus.OK))
                .execute();

        assertThat(evalXpath(response.getBody(),
                "//monitoring:getSecurityServerMetricsResponse//monitoring:numericMetric[./monitoring:name/text()='"
                + metricName
                + "']/monitoring:value"))
                .isNotEmpty()
                .containsOnlyDigits();
    }


    @SneakyThrows
    private SoapMessageImpl buildMetricsRequest(final String queryId, final String metricName) {
        SoapHeader header = new SoapHeader();
        ClientId member = ClientId.Conf.create("CS", "GOV", "0245437-2");
        header.setClient(member);
        header.setService(ServiceId.Conf.create(member, "getSecurityServerMetrics"));
        header.setSecurityServer(SecurityServerId.Conf.create(member, "SS1"));
        header.setQueryId(queryId);
        header.setProtocolVersion(new ProtocolVersion());

        SoapBuilder builder = new SoapBuilder();
        builder.setHeader(header);
        var body = new GetSecurityServerMetricsType();
        Optional.ofNullable(metricName)
                .filter(Predicate.not(String::isEmpty))
                .map(name -> {
                    var outputSpec = new OutputSpecType();
                    outputSpec.getOutputField().add(name);
                    return outputSpec;
                })
                .ifPresent(body::setOutputSpec);

        builder.setCreateBodyCallback(soapBodyNode -> MARSHALLER.marshal(
                new JAXBElement<>(new QName("http://x-road.eu/xsd/monitoring", "getSecurityServerMetrics"),
                        GetSecurityServerMetricsType.class, null, body), soapBodyNode)
        );
        return builder.build();
    }

    @SneakyThrows
    private static Marshaller createMarshaller() {
        return JAXBContext.newInstance(ObjectFactory.class, GetSecurityServerMetricsType.class).createMarshaller();
    }

}
