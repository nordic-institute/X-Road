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

import io.cucumber.docstring.DocString;
import io.cucumber.java.en.Step;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponseOptions;
import org.niis.xroad.e2e.container.EnvSetup;
import org.niis.xroad.e2e.container.Port;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_CLIENT_ID;
import static io.restassured.RestAssured.given;
import static io.restassured.config.XmlConfig.xmlConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

@SuppressWarnings(value = {"SpringJavaInjectionPointsAutowiringInspection"})
public class ProxyStepDefs extends BaseE2EStepDefs {
    @Autowired
    private EnvSetup envSetup;

    private ValidatableResponseOptions<?, ?> response;

    @Step("SOAP request is sent to {string} proxy")
    public void requestSoapIsSentToProxy(String targetProxy, DocString docString) {
        var mapping = envSetup.getContainerMapping(targetProxy, Port.PROXY);

        response = given()
                .config(RestAssured.config()
                        .xmlConfig(xmlConfig()
                                .namespaceAware(true)
                                .declareNamespace("soapenv", "http://schemas.xmlsoap.org/soap/envelope/")))
                .body(docString.getContent())
                .header(HttpHeaders.CONTENT_TYPE, "text/xml")
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
        response.assertThat()
                .statusCode(httpStatus)
                .body(path, notNullValue());
    }

    @Step("REST request is sent to {string} proxy")
    public void requestRestIsSentToProxy(String targetProxy, DocString docString) {
        var mapping = envSetup.getContainerMapping(targetProxy, Port.PROXY);

        response = given()
                .body(docString.getContent())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HEADER_CLIENT_ID, "DEV/COM/4321/TestClient")
                .post("http://%s:%s/r1/DEV/COM/1234/TestService/mock1".formatted(mapping.host(), mapping.port()))
                .then();
    }


}
