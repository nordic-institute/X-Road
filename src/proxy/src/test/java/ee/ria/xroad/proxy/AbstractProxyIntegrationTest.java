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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.testutil.IntegrationTest;
import ee.ria.xroad.proxy.testutil.TestGlobalConfWithDs;
import ee.ria.xroad.proxy.testutil.TestKeyConf;
import ee.ria.xroad.proxy.testutil.TestServerConf;
import ee.ria.xroad.proxy.testutil.TestService;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.niis.xroad.edc.management.client.configuration.EdcManagementApiFactory;
import org.niis.xroad.proxy.edc.AssetsRegistrationJob;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static ee.ria.xroad.common.SystemProperties.OCSP_RESPONDER_LISTEN_ADDRESS;
import static ee.ria.xroad.common.SystemProperties.PROXY_SERVER_LISTEN_ADDRESS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Base class for proxy integration tests
 * Starts and stops the test proxy instance and a service simulator.
 */
@Slf4j
@Category(IntegrationTest.class)
public abstract class AbstractProxyIntegrationTest {
    private static final Set<Integer> RESERVED_PORTS = new HashSet<>();

    private static GenericApplicationContext applicationContext;

    protected static int proxyClientPort = getFreePort();
    protected static int servicePort = getFreePort();
    protected static TestService service;

    private static final TestServerConf TEST_SERVER_CONF = new TestServerConf(servicePort);
    private static final TestGlobalConfWithDs TEST_GLOBAL_CONF = new TestGlobalConfWithDs();

    private static Process consumerProcess;
    private static Process providerProcess;

    @Rule
    public final ExternalResource serviceResource = new ExternalResource() {
        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        before();
                        service.before();
                        try {
                            base.evaluate();
                        } finally {
                            service.assertOk();
                        }
                    } finally {
                        after();
                    }
                }
            };
        }
    };

    static class TestProxyMain extends ProxyMain {
        @Override
        protected void loadSystemProperties() {
            System.setProperty(SystemProperties.CONF_PATH, "build/resources/test/etc/");
            System.setProperty(SystemProperties.PROXY_CONNECTOR_HOST, "127.0.0.1");
            System.setProperty(SystemProperties.PROXY_CLIENT_HTTP_PORT, String.valueOf(proxyClientPort));
            System.setProperty(SystemProperties.PROXY_CLIENT_HTTPS_PORT, String.valueOf(getFreePort()));

            final String serverPort = String.valueOf(getFreePort());
            System.setProperty(SystemProperties.PROXY_SERVER_LISTEN_PORT, serverPort);
            System.setProperty(SystemProperties.PROXY_SERVER_PORT, serverPort);
//            System.setProperty(SystemProperties.PROXY_EDC_LISTEN_PORT, String.valueOf(getFreePort())); TODO

            System.setProperty(SystemProperties.OCSP_RESPONDER_PORT, String.valueOf(getFreePort()));
            System.setProperty(SystemProperties.JETTY_CLIENTPROXY_CONFIGURATION_FILE, "src/test/clientproxy.xml");
            System.setProperty(SystemProperties.JETTY_SERVERPROXY_CONFIGURATION_FILE, "src/test/serverproxy.xml");
            System.setProperty(SystemProperties.JETTY_EDCPROXY_CONFIGURATION_FILE, "src/test/edcproxy.xml");
            System.setProperty(SystemProperties.JETTY_OCSP_RESPONDER_CONFIGURATION_FILE, "src/test/ocsp-responder.xml");
            System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/");

            System.setProperty(PROXY_SERVER_LISTEN_ADDRESS, "127.0.0.1");
            System.setProperty(OCSP_RESPONDER_LISTEN_ADDRESS, "127.0.0.1");

            System.setProperty(SystemProperties.PROXY_CLIENT_TIMEOUT, "15000");
            System.setProperty(SystemProperties.DATABASE_PROPERTIES, "src/test/resources/hibernate.properties");

            System.setProperty(SystemProperties.PROXY_HEALTH_CHECK_PORT, "5558");
            System.setProperty(SystemProperties.SERVER_CONF_CACHE_PERIOD, "0");

            System.setProperty(SystemProperties.GRPC_INTERNAL_TLS_ENABLED, Boolean.FALSE.toString());
            System.setProperty(SystemProperties.DATASPACES_ENABLED, Boolean.TRUE.toString());

            super.loadSystemProperties();
        }

        @Override
        protected void loadGlobalConf() {
            KeyConf.reload(new TestKeyConf());
            ServerConf.reload(TEST_SERVER_CONF);
            GlobalConf.reload(TEST_GLOBAL_CONF);

            prepareServerEdc();
        }
    }

    @Configuration
    static class TestProxySpringConfig {

        @Bean(initMethod = "start", destroyMethod = "stop")
        TestService testService() {
            service = new TestService(servicePort);
            return service;
        }
    }

    @BeforeClass
    public static void setup() throws Exception {
        startEdcProvider();
        startEdcConsumer();

        //
        applicationContext = new TestProxyMain().createApplicationContext(TestProxySpringConfig.class);
    }

    @SneakyThrows
    private static void prepareServerEdc() {
        EdcManagementApiFactory apiFactory = new EdcManagementApiFactory("http://localhost:19193");

        var assetRegistrationJob = new AssetsRegistrationJob(apiFactory.dataplaneSelectorApi(),
                apiFactory.assetsApi(), apiFactory.policyDefinitionApi(), apiFactory.contractDefinitionApi());
        assetRegistrationJob.registerDataPlane();
        assetRegistrationJob.registerAssets();
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

    static int getFreePort() {
        while (true) {
            try (ServerSocket ss = new ServerSocket(0)) {
                final int port = ss.getLocalPort();
                if (RESERVED_PORTS.add(port)) {
                    return port;
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
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
