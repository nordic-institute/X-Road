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
package org.niis.xroad.proxy.core;


import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestMessage;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.niis.xroad.proxy.core.test.TestService;
import org.niis.xroad.serverconf.model.DescriptionType;
import org.niis.xroad.test.globalconf.TestGlobalConf;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static ee.ria.xroad.common.util.JettyUtils.getContentLength;
import static ee.ria.xroad.common.util.JettyUtils.setContentType;
import static io.restassured.RestAssured.given;
import static org.eclipse.jetty.io.Content.Sink.asOutputStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * RestProxyTest
 */
@Slf4j
public class RestProxyTest extends AbstractProxyIntegrationTest {

    static final String PREFIX = "/r" + RestMessage.PROTOCOL_VERSION;

    @Test
    public void shouldFailIfClientHeaderMissing() {
        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue());
    }

    @Test
    public void shouldFailIfMalformedRequestURI() {
        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .get(PREFIX + "/invalid")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue());
    }

    @Test
    public void shouldHandleSimplePost() {
        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .body("{\"value\" : 42}")
                .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .then()
                .statusCode(200)
                .body("value", Matchers.equalTo(42));
    }

    @Test
    public void shouldKeepQueryId() {
        final String qid = UUID.randomUUID().toString();
        setServiceHandler((request, response) -> {
            assertEquals(qid, request.getHeaders().get("X-Road-Id"));
        });

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .header("X-Road-Id", qid)
                .body("{\"value\" : 42}")
                .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .then()
                .statusCode(200)
                .header("X-Road-Id", qid);

    }

    @Test
    public void shouldHaveContentLengthHeader() {
        String body = "{\"value\" : 42}";
        setServiceHandler((request, response) -> {
            assertEquals(body.getBytes().length,
                    getContentLength(request));
        });

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .body(body)
                .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .then()
                .statusCode(200);
    }

    @Test
    public void requestHashShouldBeUnique() {
        final String qid = "queryid";
        final String requestHash = given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .header("X-Road-Id", qid)
                .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .header(MimeUtils.HEADER_REQUEST_HASH);

        assertNotNull(requestHash);

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .header("X-Road-Id", qid)
                .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .then()
                .header(MimeUtils.HEADER_REQUEST_HASH, Matchers.not(requestHash));
    }

    @Test
    public void shouldHaveOnlyOneDateHeader() {
        assertEquals(1, given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .body("{\"value\" : 42}")
                .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .headers().getValues("Date").size());
    }

    @Test
    public void shouldAcceptPercentEncodedIdentifiers() {
        setServiceHandler((request, response) -> assertEquals("/path%3B/", request.getHttpURI().getPath()));
        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub%2Fsystem")
                .urlEncodingEnabled(false)
                .get(PREFIX + "/EE/BUSINESS/producer/s%2Fub/%C3%B6%C3%A4%C3%A5/path%3B/")
                .then().statusCode(200);
    }

    @Test
    public void shouldAcceptEmptyPath() {
        setServiceHandler((request, response) -> assertEquals("/", request.getHttpURI().getPath()));
        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/service/");
    }

    @Test
    public void shouldGetLargeBinaryMessage() throws Exception {
        setServiceHandler(LARGE_OBJECT_HANDLER);
        final int requestedBytes = 13 * 1024 * 1024 + 104729;

        final InputStream stream = given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .queryParam("bytes", requestedBytes)
                .get(PREFIX + "/EE/BUSINESS/producer/sub/test").asInputStream();

        long c = 0;
        int r;
        byte[] buf = new byte[8192];
        while ((r = stream.read(buf)) >= 0) {
            c += r;
        }
        assertEquals(requestedBytes, c);
        stream.close();
    }

    @Test
    public void shouldNotFollow302Redirects() {
        final String location = PREFIX + "/EE/BUSINESS/producer/sub/notexists";
        setServiceHandler((request, response) -> {
            response.setStatus(302);
            response.getHeaders().put("Location", location);
        });
        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .redirects().follow(false)
                .get(PREFIX + "/EE/BUSINESS/producer/sub/service")
                .then()
                .statusCode(302)
                .header("Location", location);
    }

    @Test
    public void shouldNotAllowCallingWSDLServices() {
        TEST_SERVER_CONF.setServerConfProvider(new TestServiceServerConf() {
            @Override
            public DescriptionType getDescriptionType(ServiceId service) {
                if ("wsdl".equals(service.getServiceCode())) {
                    return DescriptionType.WSDL;
                }
                return DescriptionType.REST;
            }
        });

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(500)
                .header("X-Road-Error", "Server.ServerProxy.ServiceType");
    }

    @Test
    public void shouldNotAllowPATCH() {
        TEST_SERVER_CONF.setServerConfProvider(new TestServiceServerConf() {
            @Override
            public boolean isQueryAllowed(ClientId sender, ServiceId service, String method, String path) {
                if ("PATCH".equalsIgnoreCase(method)) return false;
                return super.isQueryAllowed(sender, service, method, path);
            }
        });

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/service")
                .then()
                .statusCode(200);

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .patch(PREFIX + "/EE/BUSINESS/producer/sub/service")
                .then()
                .statusCode(500)
                .header("X-Road-Error", "Server.ServerProxy.AccessDenied");
    }

    @Test
    public void shouldSelectResolvableAddress() {

        TEST_GLOBAL_CONF.setGlobalConfProvider(new TestGlobalConf() {
            @Override
            public Collection<String> getProviderAddress(ClientId provider) {
                return Arrays.asList("127.0.0.1", "server.invalid.", "127.0.0,78", "\ufeffzero\u200B.width", "::1");
            }
        });

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .body("{\"value\" : 42}")
                .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .then()
                .statusCode(200)
                .body("value", Matchers.equalTo(42));
    }

    @Test
    public void shouldRespectAcceptHeaderInErrorResponse() {

        TEST_SERVER_CONF.setServerConfProvider(new TestServiceServerConf() {
            @Override
            public DescriptionType getDescriptionType(ServiceId service) {
                if ("wsdl".equals(service.getServiceCode())) {
                    return DescriptionType.WSDL;
                }
                return DescriptionType.REST;
            }
        });

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json;charset=utf-8")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "application/json");

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("Accept", "foobarbaz")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "application/json");

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "application/json");

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("Accept", "application/xml")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "application/xml;charset=utf-8");

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("Accept", "text/xml")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "text/xml;charset=utf-8");

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("Accept", "application/xml, text/xml")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "application/xml;charset=utf-8");

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("Accept", "text/xml, application/xml")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "text/xml;charset=utf-8");

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("Accept", "text/xml-patch+xml")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "application/json");

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("Accept", "application/xml; q=0.2")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "application/xml;charset=utf-8");

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("Accept", "text/xml, application/xml, application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "text/xml;charset=utf-8");

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("Accept", "text/*")
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/wsdl")
                .then()
                .statusCode(Matchers.is(500))
                .header("X-Road-Error", Matchers.notNullValue())
                .header("Content-Type", "text/xml;charset=utf-8");
    }

    private static final TestService.Handler LARGE_OBJECT_HANDLER = (request, response) -> {
        response.setStatus(200);
        setContentType(response, "application/octet-stream");
        final int bytes = Integer.parseInt(Request.getParameters(request).getValue("bytes"));
        final var output = asOutputStream(response);
        byte[] buf = new byte[8192];
        Arrays.fill(buf, (byte) (bytes % 256));
        int i = bytes;
        for (; i > buf.length; i -= buf.length) {
            output.write(buf);
        }
        output.write(buf, 0, i);
        output.close();
    };
}
