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
package ee.ria.xroad.proxy;


import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestMessage;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.testutil.TestGlobalConf;
import ee.ria.xroad.proxy.testutil.TestGlobalConfWithDs;
import ee.ria.xroad.proxy.testutil.TestServerConf;
import ee.ria.xroad.proxy.testutil.TestService;

import jakarta.servlet.ServletOutputStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.niis.xroad.edc.management.client.configuration.EdcManagementApiFactory;
import org.niis.xroad.proxy.edc.AssetsRegistrationJob;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * RestEdcProxyTest
 */
@Slf4j
public class RestEdcProxyTest extends AbstractProxyIntegrationTest {

    static final String PREFIX = "/r" + RestMessage.PROTOCOL_VERSION;

    private static final TestGlobalConfWithDs TEST_GLOBAL_CONF = new TestGlobalConfWithDs();

    private static Process consumerProcess;
    private static Process providerProcess;

    public static Map<String, String> getAdditionalSystemParameters() {
        return Map.of(
                SystemProperties.DATASPACES_ENABLED, Boolean.TRUE.toString(),
                SystemProperties.DATASPACES_CONTROL_PORT, "19192",
                SystemProperties.DATASPACES_MANAGEMENT_PORT, "29193", // here 2
                SystemProperties.DATASPACES_PUBLIC_PORT, "19291",
                SystemProperties.DATASPACES_PROTOCOL_PORT, "19194",
                SystemProperties.JETTY_EDCPROXY_CONFIGURATION_FILE, "src/test/edcproxy.xml"
                // SystemProperties.PROXY_EDC_LISTEN_PORT, String.valueOf(getFreePort()) TODO
        );
    }

    @BeforeClass
    public static void setup() throws Exception {
        startEdcProvider();
        startEdcConsumer();

        applicationContext = new TestProxyMain(getAdditionalSystemParameters(), TEST_GLOBAL_CONF, RestEdcProxyTest::prepareServerEdc)
                .createApplicationContext(TestProxySpringConfig.class);
    }

    @AfterClass
    public static void teardown() {
        if (applicationContext != null) {
            applicationContext.close();
        }
        RESERVED_PORTS.clear();

        providerProcess.descendants().forEach(ProcessHandle::destroy);
        consumerProcess.descendants().forEach(ProcessHandle::destroy);
        providerProcess.destroy();
        consumerProcess.destroy();
    }

    @After
    public void after() {
        ServerConf.reload(TEST_SERVER_CONF);
        GlobalConf.reload(TEST_GLOBAL_CONF);
    }

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
                .post(PREFIX + "/EE/GOV/1234TEST_CLIENT/SUBCODE5/SERVICE2")
                .then()
                .statusCode(200)
                .body("value", Matchers.equalTo(42));
    }

    @Test
    @Ignore("edc does not support proxying custom headers to provider IS")
    // todo: xroad8
    public void shouldKeepQueryId() {
        final String qid = UUID.randomUUID().toString();
        service.setHandler((target, request, response) -> assertEquals(qid, request.getHeader("X-Road-Id")));

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .header("X-Road-Id", qid)
                .body("{\"value\" : 42}")
                .post(PREFIX + "/EE/GOV/1234TEST_CLIENT/SUBCODE5/SERVICE2")
                .then()
                .statusCode(200)
                .header("X-Road-Id", qid);
    }

    @Test
    @Ignore("header not passed to provider IS")
    // todo: xroad8
    public void shouldHaveContentLengthHeader() {
        String body = "{\"value\" : 42}";
        service.setHandler((target, request, response) -> assertEquals(body.getBytes().length,
                request.getContentLength()));

        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .body(body)
                .post(PREFIX + "/EE/GOV/1234TEST_CLIENT/SUBCODE5/SERVICE2")
                .then()
                .statusCode(200);
    }

    @Test
    @Ignore("provider edc does not calculate request hash")
    //todo: xroad8
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
    @Ignore("cant't control how EDC calls provider IS")
    //todo: xroad8
    public void shouldAcceptPercentEncodedIdentifiers() {
        service.setHandler((target, request, response) -> assertEquals("/path%3B/", request.getRequestURI()));
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
        service.setHandler((target, request, response) -> assertEquals("/", request.getRequestURI()));
        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("X-Road-Client", "EE/BUSINESS/consumer/subsystem")
                .get(PREFIX + "/EE/BUSINESS/producer/sub/service/");
    }

    @Test
    public void shouldGetLargeBinaryMessage() throws Exception {
        service.setHandler(LARGE_OBJECT_HANDLER);
        final int requestedBytes = 13 * 1024 * 1024 + 104729;

        final InputStream stream = given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .queryParam("bytes", requestedBytes)
                .get(PREFIX + "/EE/GOV/1234TEST_CLIENT/SUBCODE5/SERVICE2").asInputStream();

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
    @Ignore
    //todo: xroad8
    public void shouldNotFollow302Redirects() {
        final String location = PREFIX + "/EE/BUSINESS/producer/sub/notexists";
        service.setHandler((target, request, response) -> {
            response.setStatus(302);
            response.setHeader("Location", location);
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
    @Ignore
    //todo: xroad8
    public void shouldNotAllowCallingWSDLServices() {
        ServerConf.reload(new TestServerConf(servicePort) {
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
    @Ignore
    //todo: xroad8
    public void shouldNotAllowPATCH() {
        ServerConf.reload(new TestServerConf(servicePort) {
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

        GlobalConf.reload(new TestGlobalConf() {
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

        ServerConf.reload(new TestServerConf(servicePort) {
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

    private static final TestService.Handler LARGE_OBJECT_HANDLER = (target, request, response) -> {
        response.setStatus(200);
        response.setContentType("application/octet-stream");
        final int bytes = Integer.parseInt(request.getParameter("bytes"));
        final ServletOutputStream output = response.getOutputStream();
        byte[] buf = new byte[8192];
        Arrays.fill(buf, (byte) (bytes % 256));
        int i = bytes;
        for (; i > buf.length; i -= buf.length) {
            output.write(buf);
        }
        output.write(buf, 0, i);
        output.close();
    };

    @SneakyThrows
    private static void prepareServerEdc() {
        EdcManagementApiFactory apiFactory = new EdcManagementApiFactory(
                "http://localhost:19193".formatted("19193"));

        var assetRegistrationJob = new AssetsRegistrationJob(apiFactory.dataplaneSelectorApi(),
                apiFactory.assetsApi(), apiFactory.policyDefinitionApi(), apiFactory.contractDefinitionApi());
        assetRegistrationJob.registerDataPlane();
        assetRegistrationJob.registerAssets();
    }

    private static void startEdcProvider() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("./run-provider.sh", "--in-memory");
                pb.directory(new File("../security-server/edc/"));

                providerProcess = pb.start();
                // Redirect output and error streams to SLF4J
                var logger = LoggerFactory.getLogger("EDC-PROVIDER");
                StreamGobbler outputGobbler = new StreamGobbler(providerProcess.getInputStream(), logger::info);
                StreamGobbler errorGobbler = new StreamGobbler(providerProcess.getErrorStream(), logger::error);

                // Start gobbling the streams
                outputGobbler.start();
                errorGobbler.start();

            } catch (Exception e) {
                log.error("Error", e);
            }
        });

        t.start();
        t.join();
        MILLISECONDS.sleep(3000);
    }

    private static void startEdcConsumer() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("./run-consumer.sh", "--in-memory");
                pb.directory(new File("../security-server/edc/"));
                consumerProcess = pb.start();
                // Redirect output and error streams to SLF4J
                var logger = LoggerFactory.getLogger("EDC-CONSUMER");
                StreamGobbler outputGobbler = new StreamGobbler(consumerProcess.getInputStream(), logger::info);
                StreamGobbler errorGobbler = new StreamGobbler(consumerProcess.getErrorStream(), logger::error);

                // Start gobbling the streams
                outputGobbler.start();
                errorGobbler.start();

            } catch (Exception e) {
                log.error("Error", e);
            }
        });

        t.start();
        t.join();
        MILLISECONDS.sleep(1000);
    }

    private static class StreamGobbler extends Thread {
        private final InputStream inputStream;
        private final Consumer<String> consumeInputLine;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumeInputLine) {
            this.inputStream = inputStream;
            this.consumeInputLine = consumeInputLine;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    consumeInputLine.accept(line);
                }
            } catch (IOException e) {
                //do nothing
            }
        }
    }
}
