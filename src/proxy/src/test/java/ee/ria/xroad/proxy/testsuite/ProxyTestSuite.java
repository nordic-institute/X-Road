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
package ee.ria.xroad.proxy.testsuite;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestPortUtils;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.proxy.ProxyMain;
import ee.ria.xroad.proxy.addon.AddOn;
import ee.ria.xroad.proxy.clientproxy.ClientProxy;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.serverproxy.ServerProxy;
import ee.ria.xroad.proxy.util.CertHashBasedOcspResponder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.server.RpcServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.String.valueOf;

/**
 * Proxy test suite program.
 */
@Slf4j
public final class ProxyTestSuite {
    public static final int SERVICE_PORT = 8081;
    public static final int SERVICE_SSL_PORT = 8088;

    static volatile MessageTestCase currentTestCase;

    private static ClientProxy clientProxy;
    private static ServerProxy serverProxy;

    private static JobManager jobManager;
    private static RpcServer proxyRpcServer;

    private ProxyTestSuite() {
    }

    /**
     * Main program entry point.
     *
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {

        setPropsIfNotSet();

        setUp();

        List<MessageTestCase> testCasesToRun = TestcaseLoader.getTestCasesToRun(args);

        List<MessageTestCase> normalTestCases = new ArrayList<>();
        List<MessageTestCase> sslTestCases = new ArrayList<>();
        List<MessageTestCase> isolatedSslTestCases = new ArrayList<>();

        for (MessageTestCase tc : testCasesToRun) {
            if (tc instanceof IsolatedSslMessageTestCase) {
                isolatedSslTestCases.add(tc);
            } else if (tc instanceof SslMessageTestCase) {
                sslTestCases.add(tc);
            } else {
                normalTestCases.add(tc);
            }
        }

        startWatchdog();

        try {
            MessageLog.init(jobManager);
            OpMonitoring.init();

            runNormalTestCases(normalTestCases);
            runSslTestCases(sslTestCases);
            runIsolatedSslTestCases(isolatedSslTestCases);

        } finally {
            MessageLog.shutdown();
            OpMonitoring.shutdown();
            jobManager.stop();

            List<MessageTestCase> failed = getFailedTestcases(testCasesToRun);

            log.info("COMPLETE, passed {} - failed {}", testCasesToRun.size() - failed.size(), failed.size());

            StringBuilder sb = new StringBuilder("Results:\n");

            for (MessageTestCase t : testCasesToRun) {
                String status = t.isFailed() ? "FAILED" : "PASSED";
                sb.append("\t").append(status).append(" - ");
                sb.append(t.getId()).append("\n");
            }

            if (failed.isEmpty()) {
                log.info("{}", sb.toString());
            } else {
                log.warn("{}", sb.toString());
            }

            System.exit(failed.isEmpty() ? 0 : 1);
        }
    }

    @SneakyThrows
    private static void setPropsIfNotSet() {

        PropsSolver solver = new PropsSolver();

        solver.setIfNotSet(SystemProperties.PROXY_CLIENT_HTTP_PORT, valueOf(TestPortUtils.findRandomPort()));
        solver.setIfNotSet(SystemProperties.PROXY_CLIENT_HTTPS_PORT, valueOf(TestPortUtils.findRandomPort()));
        final var proxyPort = valueOf(TestPortUtils.findRandomPort());
        solver.setIfNotSet(SystemProperties.PROXY_SERVER_LISTEN_PORT, proxyPort);
        solver.setIfNotSet(SystemProperties.PROXY_SERVER_PORT, proxyPort);
        solver.setIfNotSet(SystemProperties.JETTY_CLIENTPROXY_CONFIGURATION_FILE, "src/test/clientproxy.xml");
        solver.setIfNotSet(SystemProperties.JETTY_SERVERPROXY_CONFIGURATION_FILE, "src/test/serverproxy.xml");
        solver.setIfNotSet(SystemProperties.JETTY_OCSP_RESPONDER_CONFIGURATION_FILE, "src/test/ocsp-responder.xml");
        solver.setIfNotSet(SystemProperties.TEMP_FILES_PATH, "build/");
    }

    private static class PropsSolver {
        private final Set<String> setProperties = System.getProperties().stringPropertyNames();

        void setIfNotSet(String property, String defaultValue) {
            if (!setProperties.contains(property)) {
                System.setProperty(property, defaultValue);
            }
        }
    }

    private static void setUp() throws Exception {
        KeyConf.reload(new TestSuiteKeyConf());
        ServerConf.reload(new TestSuiteServerConf());
        GlobalConf.reload(new TestSuiteGlobalConf());

        System.setProperty(SystemProperties.PROXY_CLIENT_TIMEOUT, "15000");
        System.setProperty(SystemProperties.DATABASE_PROPERTIES, "src/test/resources/hibernate.properties");

        jobManager = new JobManager();
        jobManager.start();

        AddOn.BindableServiceRegistry serviceRegistry = new AddOn.BindableServiceRegistry();
        for (AddOn addon : ServiceLoader.load(AddOn.class)) {
            addon.init(serviceRegistry);
        }

        proxyRpcServer = ProxyMain.createRpcServer(serviceRegistry.getRegisteredServices());
    }

    private static void runNormalTestCases(List<MessageTestCase> tc) throws Exception {
        if (tc.isEmpty()) {
            return;
        }

        log.info("=============================");
        log.info("Running non-SSL test cases...");
        log.info("=============================");

        // Make sure SSL is disabled
        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "false");

        runTestSuite(getDefaultServices(), tc);
    }

    private static void runSslTestCases(List<MessageTestCase> tc) throws Exception {
        if (tc.isEmpty()) {
            return;
        }

        log.info("=========================");
        log.info("Running SSL test cases...");
        log.info("=========================");

        // Make sure SSL is enabled
        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "true");

        List<StartStop> services = getDefaultServices();
        services.add(new DummySslServerProxy());

        runTestSuite(services, tc);
    }

    private static void runIsolatedSslTestCases(List<MessageTestCase> tc) throws Exception {
        if (tc.isEmpty()) {
            return;
        }

        log.info("=========================");
        log.info("Running Isolated SSL test cases...");
        log.info("=========================");

        // Make sure SSL is enabled
        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "true");

        for (MessageTestCase c : tc) {
            List<StartStop> services = getDefaultServices();
            services.add(new DummySslServerProxy());
            runTestSuite(services, Collections.singletonList(c));
        }
    }

    private static void runTestSuite(List<StartStop> services, List<MessageTestCase> tc) throws Exception {
        for (StartStop s : services) {
            try {
                s.start();

                log.info(s.getClass().getSimpleName() + " started");
            } catch (Exception e) {
                log.error("Failed to start service", e);
            }
        }

        try {
            runTestCases(tc);
        } finally {
            for (StartStop s : services) {
                s.stop();
            }
        }
    }

    private static void runTestCases(List<MessageTestCase> tc) throws Exception {
        for (MessageTestCase t : tc) {
            currentTestCase = t;

            log.info("TESTCASE START: {}", t.getId());

            try {
                t.execute();

                log.info("TESTCASE PASSED: {}", t.getId());
            } catch (Exception | AssertionError e) {
                t.setFailed(true);

                log.info("TESTCASE FAILED: " + t.getId(), e);
            } catch (Error e) {
                t.setFailed(true);
                log.error("TESTCASE FAILED: " + t.getId(), e);
                throw e;
            } finally {
                // We close all idle connections after each testcase to provide
                // clean connection pool for the next testcase. This comes
                // in handy for SSL testcases, where we want to do
                // SSL handshake for each consequtive request.
                serverProxy.closeIdleConnections();
            }
        }
    }

    private static List<MessageTestCase> getFailedTestcases(List<MessageTestCase> tc) {
        List<MessageTestCase> failed = new ArrayList<>();

        for (MessageTestCase t : tc) {
            if (t.isFailed()) {
                failed.add(t);
            }
        }

        return failed;
    }

    private static List<StartStop> getDefaultServices() throws Exception {
        clientProxy = new ClientProxy();
        // listen at localhost to let dummy proxy listen at 127.0.0.2
        serverProxy = new ServerProxy("127.0.0.1");

        return new ArrayList<>(// need mutable list
                Arrays.asList(clientProxy, serverProxy, new CertHashBasedOcspResponder("127.0.0.1"),
                        proxyRpcServer,
                        new DummyService(), new DummyServerProxy()));
    }

    private static void startWatchdog() {
        // New timer with daemon thread.
        Timer timer = new Timer(true);

        // Schedule task to kill the test.
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                log.error("Test suite is taking too long, exiting");

                System.exit(2);
            }
        }, 20 * 60 * 1000); // 20 minutes.
    }
}
