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

import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.addons.api.FeignXRoadSoapRequestsApi;
import org.niis.xroad.ss.test.ui.glue.BaseUiStepDefs;
import org.springframework.beans.factory.annotation.Autowired;

public class ProxyMonitorStepDefs extends BaseUiStepDefs {

    @Autowired
    private FeignXRoadSoapRequestsApi securityServerMetricsRequestsApi;

    @SuppressWarnings("checkstyle:OperatorWrap")
    @Step("Security Server Metrics request was sent")
    public void executeGetSecurityServerMetricsRequest() {
        String requestBody = "<SOAP-ENV:Envelope\n" +
                "\txmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
                "\txmlns:id=\"http://x-road.eu/xsd/identifiers\"\n" +
                "\txmlns:xrd=\"http://x-road.eu/xsd/xroad.xsd\"\n" +
                "\txmlns:m=\"http://x-road.eu/xsd/monitoring\">\n" +
                "    <SOAP-ENV:Header>\n" +
                "        <xrd:client id:objectType=\"MEMBER\">\n" +
                "            <id:xRoadInstance>CS</id:xRoadInstance>\n" +
                "            <id:memberClass>GOV</id:memberClass>\n" +
                "            <id:memberCode>0245437-2</id:memberCode>\n" +
                "        </xrd:client>\n" +
                "        <xrd:service id:objectType=\"SERVICE\">\n" +
                "            <id:xRoadInstance>CS</id:xRoadInstance>\n" +
                "            <id:memberClass>GOV</id:memberClass>\n" +
                "            <id:memberCode>0245437-2</id:memberCode>\n" +
                "            <id:serviceCode>getSecurityServerMetrics</id:serviceCode>\n" +
                "        </xrd:service>\n" +
                "        <xrd:securityServer id:objectType=\"SERVER\">\n" +
                "            <id:xRoadInstance>CS</id:xRoadInstance>\n" +
                "            <id:memberClass>GOV</id:memberClass>\n" +
                "            <id:memberCode>0245437-2</id:memberCode>\n" +
                "            <id:serverCode>SS1</id:serverCode>\n" +
                "        </xrd:securityServer>\n" +
                "        <xrd:id>ID1234</xrd:id>\n" +
                "        <xrd:protocolVersion>4.0</xrd:protocolVersion>\n" +
                "    </SOAP-ENV:Header>\n" +
                "    <SOAP-ENV:Body>\n" +
                "        <m:getSecurityServerMetrics/>\n" +
                "    </SOAP-ENV:Body>\n" +
                "</SOAP-ENV:Envelope>";
        securityServerMetricsRequestsApi.getSecurityServerMetrics(requestBody.getBytes());
    }

}
