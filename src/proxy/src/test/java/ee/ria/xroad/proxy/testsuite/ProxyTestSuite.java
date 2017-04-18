/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.proxy.clientproxy.ClientProxy;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.serverproxy.ServerProxy;
import ee.ria.xroad.proxy.util.CertHashBasedOcspResponder;

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
    private static ActorSystem actorSystem;

    private ProxyTestSuite() {
    }

    /**
     * Main program entry point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        System.setProperty(SystemProperties.PROXY_CLIENT_HTTP_PORT, "8080");
        System.setProperty(SystemProperties.PROXY_CLIENT_HTTPS_PORT, "8443");
        System.setProperty(SystemProperties.JETTY_CLIENTPROXY_CONFIGURATION_FILE, "src/test/clientproxy.xml");
        System.setProperty(SystemProperties.JETTY_SERVERPROXY_CONFIGURATION_FILE, "src/test/serverproxy.xml");
        System.setProperty(SystemProperties.JETTY_OCSP_RESPONDER_CONFIGURATION_FILE, "src/test/ocsp-responder.xml");
        System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/");

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
            MessageLog.init(actorSystem, jobManager);
            OpMonitoring.init(actorSystem);

            runNormalTestCases(normalTestCases);
            runSslTestCases(sslTestCases);
            runIsolatedSslTestCases(isolatedSslTestCases);

        } finally {
            jobManager.stop();
            actorSystem.terminate();

            List<MessageTestCase> failed = getFailedTestcases(testCasesToRun);

            log.info("COMPLETE, passed {} - failed {}", testCasesToRun.size() - failed.size(), failed.size());

            StringBuilder sb = new StringBuilder("Results:\n");

            for (MessageTestCase t : testCasesToRun) {
                String status = t.isFailed() ? "FAILED" : "PASSED";
                sb.append("\t").append(status).append(" - ");
                sb.append(t.getId()).append("\n");
            }

            log.info("{}", sb.toString());

            if (!failed.isEmpty()) {
                sb = new StringBuilder("Failed testcases:\n");

                for (MessageTestCase t : failed) {
                    sb.append("\t").append(t.getId()).append("\n");
                }

                throw new RuntimeException(sb.toString());
            }
        }
    }

    private static void setUp() throws Exception {
        KeyConf.reload(new TestKeyConf());
        ServerConf.reload(new TestServerConf());
        GlobalConf.reload(new TestGlobalConf());

        System.setProperty(SystemProperties.PROXY_CLIENT_TIMEOUT, "15000");
        System.setProperty(SystemProperties.DATABASE_PROPERTIES, "src/test/resources/hibernate.properties");

        jobManager = new JobManager();
        jobManager.start();

        actorSystem = ActorSystem.create("Proxy", ConfigFactory.load().getConfig("proxy"));
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
        for (StartStop s: services) {
            s.start();

            log.info(s.getClass().getSimpleName() + " started");
        }

        Thread.sleep(2000); // give time to start up

        try {
            runTestCases(tc);
        } finally {
            for (StartStop s: services) {
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
            } catch (Exception e) {
                t.setFailed(true);

                log.info("TESTCASE FAILED: " + t.getId(), e);
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
