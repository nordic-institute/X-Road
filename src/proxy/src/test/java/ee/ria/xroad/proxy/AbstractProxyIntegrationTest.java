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
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.proxy.addon.AddOn;
import ee.ria.xroad.proxy.clientproxy.ClientProxy;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.serverproxy.ServerProxy;
import ee.ria.xroad.proxy.testutil.IntegrationTest;
import ee.ria.xroad.proxy.testutil.TestGlobalConf;
import ee.ria.xroad.proxy.testutil.TestKeyConf;
import ee.ria.xroad.proxy.testutil.TestServerConf;
import ee.ria.xroad.proxy.testutil.TestService;
import ee.ria.xroad.proxy.util.CertHashBasedOcspResponder;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.net.ServerSocket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Base class for proxy integration tests
 * Starts and stops the test proxy instance and a service simulator.
 */
@Category(IntegrationTest.class)
public abstract class AbstractProxyIntegrationTest {

    private static final Set<Integer> RESERVED_PORTS = new HashSet<>();

    private static JobManager jobManager;
    private static ClientProxy clientProxy;
    private static ServerProxy serverProxy;
    private static List<StartStop> services;
    protected static int proxyClientPort = getFreePort();
    protected static int servicePort = getFreePort();
    protected static TestService service;

    private static TestServerConf testServerConf = new TestServerConf(servicePort);
    private static TestGlobalConf testGlobalConf = new TestGlobalConf();

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

    @BeforeClass
    public static void setup() throws Exception {
        System.setProperty(SystemProperties.CONF_PATH, "build/resources/test/etc/");
        System.setProperty(SystemProperties.PROXY_CONNECTOR_HOST, "127.0.0.1");
        System.setProperty(SystemProperties.PROXY_CLIENT_HTTP_PORT, String.valueOf(proxyClientPort));
        System.setProperty(SystemProperties.PROXY_CLIENT_HTTPS_PORT, String.valueOf(getFreePort()));

        final String serverPort = String.valueOf(getFreePort());
        System.setProperty(SystemProperties.PROXY_SERVER_LISTEN_PORT, serverPort);
        System.setProperty(SystemProperties.PROXY_SERVER_PORT, serverPort);

        System.setProperty(SystemProperties.OCSP_RESPONDER_PORT, String.valueOf(getFreePort()));
        System.setProperty(SystemProperties.JETTY_CLIENTPROXY_CONFIGURATION_FILE, "src/test/clientproxy.xml");
        System.setProperty(SystemProperties.JETTY_SERVERPROXY_CONFIGURATION_FILE, "src/test/serverproxy.xml");
        System.setProperty(SystemProperties.JETTY_OCSP_RESPONDER_CONFIGURATION_FILE, "src/test/ocsp-responder.xml");
        System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/");

        KeyConf.reload(new TestKeyConf());
        ServerConf.reload(testServerConf);
        GlobalConf.reload(testGlobalConf);

        System.setProperty(SystemProperties.PROXY_CLIENT_TIMEOUT, "15000");
        System.setProperty(SystemProperties.DATABASE_PROPERTIES, "src/test/resources/hibernate.properties");

        jobManager = new JobManager();

        MessageLog.init(jobManager);
        OpMonitoring.init();
        AddOn.BindableServiceRegistry serviceRegistry = new AddOn.BindableServiceRegistry();
        for (AddOn addon : ServiceLoader.load(AddOn.class)) {
            addon.init(serviceRegistry);
        }

        clientProxy = new ClientProxy();
        serverProxy = new ServerProxy("127.0.0.1");
        service = new TestService(servicePort);

        services = Arrays.asList(
                clientProxy,
                serverProxy,
                new CertHashBasedOcspResponder("127.0.0.1"),
                service
        );

        for (StartStop svc : services) {
            svc.start();
        }
    }

    @AfterClass
    public static void teardown() throws Exception {
        for (StartStop svc : services) {
            svc.stop();
            svc.join();
        }

        OpMonitoring.shutdown();
        MessageLog.shutdown();
        RESERVED_PORTS.clear();
    }

    @After
    public void after() {
        ServerConf.reload(testServerConf);
        GlobalConf.reload(testGlobalConf);
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
}
