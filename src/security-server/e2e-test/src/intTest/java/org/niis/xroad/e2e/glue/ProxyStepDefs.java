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
package org.niis.xroad.e2e.glue;

import io.cucumber.datatable.DataTable;
import io.cucumber.docstring.DocString;
import io.cucumber.java.en.Step;
import io.restassured.RestAssured;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.config.MultiPartConfig;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.ValidatableResponseOptions;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.MimeHeaders;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import org.niis.xroad.e2e.container.EnvSetup;
import org.niis.xroad.e2e.container.Port;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_CLIENT_ID;
import static io.restassured.RestAssured.given;
import static io.restassured.config.XmlConfig.xmlConfig;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;

@SuppressWarnings(value = {"SpringJavaInjectionPointsAutowiringInspection"})
public class ProxyStepDefs extends BaseE2EStepDefs {

    @Autowired
    private EnvSetup envSetup;

    private ValidatableResponseOptions<?, ?> response;
    private SOAPMessage soapMessage;

    @Step("SOAP request is sent to {string} proxy")
    public void requestSoapIsSentToProxy(String targetProxy, DocString docString) {
        requestSoapIsSentToProxy(targetProxy, "legacy", docString);
    }

    @Step("SOAP request is sent to {string} proxy using {string} transport")
    public void requestSoapIsSentToProxy(String targetProxy, String transport, DocString docString) {
        var mapping = envSetup.getContainerMapping(targetProxy, Port.PROXY);

        response = given()
                .config(RestAssured.config()
                        .xmlConfig(xmlConfig()
                                .namespaceAware(true)
                                .declareNamespace("soapenv", "http://schemas.xmlsoap.org/soap/envelope/")))
                .body(docString.getContent())
                .header(HttpHeaders.CONTENT_TYPE, "text/xml")
                .header("X-Road-Use-DS-Transport", transport.equals("legacy") ? "false" : "true")
                .post("http://%s:%s".formatted(mapping.host(), mapping.port()))
                .then();
    }

    @Step("response is sent of http status code {int} and body path {string} is equal to {string}")
    public void responseValidated(int httpStatus, String path, String value) {
        response.assertThat()
                .statusCode(httpStatus)
                .body(path, equalTo(value));
    }

    @Step("response is sent of http status code {int} and body path {string} is not empty")
    public void responseValidated(int httpStatus, String path) {
        // not working .body(path, not(emptyOrNullString()));
        String value = response.assertThat()
                .statusCode(httpStatus)
                .extract()
                .xmlPath()
                .getString(path);
        testReportService.attachText("path value", value);
        assertThat(value, not(emptyOrNullString()));
    }

    @Step("REST request is sent to {string} proxy")
    public void requestRestIsSentToProxy(String targetProxy, DocString docString) {
        requestRestIsSentToProxy(targetProxy, "legacy", docString);
    }

    @Step("REST request is sent to {string} proxy using {string} transport")
    public void requestRestIsSentToProxy(String targetProxy, String transport, DocString docString) {
        var mapping = envSetup.getContainerMapping(targetProxy, Port.PROXY);

        response = given()
                .body(docString.getContent())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HEADER_CLIENT_ID, "DEV/COM/4321/TestClient")
                .header("X-Road-Force-Legacy-Transport", transport.equals("legacy") ? "true" : "false")
                .post("http://%s:%s/r1/DEV/COM/1234/TestService/mock1".formatted(mapping.host(), mapping.port()))
                .then();
    }

    @Step("multipart MIME message with SOAP request and attachments is sent to {string} proxy")
    public void sendMultipartMimeMessageLegacy(String targetProxy, DocString docString) {
        sendMultipartMimeMessage(targetProxy, docString, "legacy");
    }

    @Step("multipart MIME message with SOAP request and attachments is sent to {string} proxy using DataSpace transport")
    public void sendMultipartMimeMessageUsingDataspace(String targetProxy, DocString docString) {
        sendMultipartMimeMessage(targetProxy, docString, "dataspace");
    }

    private void sendMultipartMimeMessage(String targetProxy, DocString docString, String transport) {
        var mapping = envSetup.getContainerMapping(targetProxy, Port.PROXY);

        response = given()
                .log().all()
                .config(RestAssured.config().multiPartConfig(
                        new MultiPartConfig()
                                .defaultCharset(StandardCharsets.UTF_8)
                                .defaultSubtype("related")))
                .multiPart("soappart", docString.getContent(), "text/xml")
                .multiPart(new MultiPartSpecBuilder("first attachment contents. size 35.")
                        .controlName("att-1")
                        .mimeType("application/octet-stream")
                        .header("Content-ID", "att-1")
                        .build())
                .multiPart(new MultiPartSpecBuilder("this is second attachment contents. size is 47.")
                        .controlName("att-2")
                        .mimeType("text/plain")
                        .header("Content-ID", "att-2")
                        .build())
                .header("X-Road-Use-DS-Transport", transport.equals("legacy") ? "false" : "true")
                .post("http://%s:%s".formatted(mapping.host(), mapping.port()))
                .then();
    }

    @Step("SOAP response contains the following attachments and sizes")
    public void validateResponseAttachments(DataTable dataTable) {
        // name: size
        Map<String, String> data = dataTable.asMap(String.class, String.class);

        response.assertThat().body("Envelope.Body.storeAttachmentsResponse.attachment.size()", equalTo(data.size()));

        for (Map.Entry<String, String> att : data.entrySet()) {
            response.assertThat()
                    .body("Envelope.Body.storeAttachmentsResponse.attachment.find { it.name == '" + att.getKey() + "'}.size",
                            equalTo(att.getValue()));
        }
    }

    @Step("response is multipart MIME message")
    public void responseIsMultipartMIMEMessage() {
        response.assertThat()
                .statusCode(HTTP_OK)
                .header(HttpHeaders.CONTENT_TYPE, startsWith("multipart/related"));
    }

    @Step("response is parsed as SOAP message")
    public void parseResponseToSoap() {
        try {
            MessageFactory factory = MessageFactory.newInstance();
            soapMessage = factory.createMessage(toMimeHeaders(response.extract().headers()),
                    response.extract().asInputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Step("multipart MIME SOAP response contains attachments with sizes")
    public void assertSoapAttachments(DataTable dataTable) throws SOAPException {
        // assert attachments count from multipart parts
        Map<String, String> data = dataTable.asMap(String.class, String.class);

        assertThat(data.size(), equalTo(soapMessage.countAttachments()));

        soapMessage.getSOAPBody().getChildNodes().getLength();

        // assert attachment names and sizes in SOAP response body
        Map<String, String> collectedFromSoapBody = new LinkedHashMap<>();
        for (int i = 0; i < soapMessage.getSOAPBody().getFirstChild().getChildNodes().getLength(); i++) {
            var node = soapMessage.getSOAPBody().getFirstChild().getChildNodes().item(i);
            String name = node.getChildNodes().item(0).getTextContent();
            String size = node.getChildNodes().item(1).getTextContent();
            collectedFromSoapBody.put(name, size);
        }

        assertThat(data, equalTo(collectedFromSoapBody));
    }

    private MimeHeaders toMimeHeaders(Headers headers) {
        MimeHeaders mimeHeaders = new MimeHeaders();
        for (Header header : headers) {
            String name = header.getName();
            String value = header.getValue();

            StringTokenizer values = new StringTokenizer(value, ",");
            while (values.hasMoreTokens()) {
                mimeHeaders.addHeader(name, values.nextToken().trim());
            }
        }
        return mimeHeaders;
    }

}
