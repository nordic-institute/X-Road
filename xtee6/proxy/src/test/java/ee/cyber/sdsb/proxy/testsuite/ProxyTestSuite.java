package ee.cyber.sdsb.proxy.testsuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.sdsb.proxy.clientproxy.ClientProxy;
import ee.cyber.sdsb.proxy.conf.KeyConf;
import ee.cyber.sdsb.proxy.conf.ServerConf;
import ee.cyber.sdsb.proxy.securelog.LogManager;
import ee.cyber.sdsb.proxy.serverproxy.ServerProxy;
import ee.cyber.sdsb.proxy.util.CertHashBasedOcspResponder;

public class ProxyTestSuite {

    private static final Logger LOG =
            LoggerFactory.getLogger(ProxyTestSuite.class);

    static final int SERVICE_PORT = 8081;

    static volatile MessageTestCase currentTestCase;

    public static void main(String[] args) throws Exception {
        KeyConf.reload(new TestKeyConf());
        ServerConf.reload(new TestServerConf());
        GlobalConf.reload(new TestGlobalConf());

        System.setProperty(SystemProperties.ASYNC_DB_PATH, "build/asyncdb");
        System.setProperty(SystemProperties.PROXY_CLIENT_TIMEOUT, "15000");

        List<MessageTestCase> testCasesToRun =
                TestcaseLoader.getTestCasesToRun(args);

        List<MessageTestCase> normalTestCases = new ArrayList<>();
        List<MessageTestCase> sslTestCases = new ArrayList<>();
        for (MessageTestCase tc : testCasesToRun) {
            if (tc instanceof SslMessageTestCase) {
                sslTestCases.add(tc);
            } else {
                normalTestCases.add(tc);
            }
        }

        startWatchdog();

        try {
            LogManager.getInstance().start();

            runNormalTestCases(normalTestCases);
            runSslTestCases(sslTestCases);
        } finally {
            LogManager.getInstance().stop();

            List<MessageTestCase> failed = getFailedTestcases(testCasesToRun);
            LOG.info("COMPLETE, passed {} - failed {}",
                    testCasesToRun.size() - failed.size(), failed.size());

            StringBuilder sb = new StringBuilder("Results:\n");
            for (MessageTestCase t : testCasesToRun) {
                String status = t.hasFailed() ? "FAILED" : "PASSED";
                sb.append("\t").append(status).append(" - ");
                sb.append(t.getId()).append("\n");
            }
            LOG.info("{}", sb.toString());

            if (!failed.isEmpty()) {
                sb = new StringBuilder("Failed testcases:\n");
                for (MessageTestCase t : failed) {
                    sb.append("\t").append(t.getId()).append("\n");
                }
                throw new RuntimeException(sb.toString());
            }
        }
    }

    private static void runNormalTestCases(List<MessageTestCase> tc)
            throws Exception {
        if (tc.isEmpty()) {
            return;
        }

        LOG.info("=============================");
        LOG.info("Running non-SSL test cases...");
        LOG.info("=============================");

        // Make sure SSL is disabled
        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "false");

        runTestSuite(getDefaultServices(), tc);
    }

    private static void runSslTestCases(List<MessageTestCase> tc)
            throws Exception {
        if (tc.isEmpty()) {
            return;
        }

        LOG.info("=========================");
        LOG.info("Running SSL test cases...");
        LOG.info("=========================");

        // Make sure SSL is enabled
        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "true");

        List<StartStop> services = getDefaultServices();
        services.add(new DummySslServerProxy());

        runTestSuite(services, tc);
    }

    private static void runTestSuite(List<StartStop> services,
            List<MessageTestCase> tc) throws Exception {
        for (StartStop s: services) {
            s.start();
            LOG.info(s.getClass().getSimpleName() + " started");
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

    private static void runTestCases(List<MessageTestCase> tc)
            throws Exception {
        for (MessageTestCase t : tc) {
            currentTestCase = t;

            LOG.info("TESTCASE START {}: Sending query: {}", t.getId(), t);
            try {
                t.execute();
                LOG.info("TESTCASE PASSED: {}", t.getId());
            } catch (Exception e) {
                t.setFailed(true);
                LOG.info("TESTCASE FAILED: " + t.getId(), e);
            }
        }
    }

    private static List<MessageTestCase> getFailedTestcases(
            List<MessageTestCase> tc) {
        List<MessageTestCase> failed = new ArrayList<>();
        for (MessageTestCase t : tc) {
            if (t.hasFailed()) {
                failed.add(t);
            }
        }

        return failed;
    }

    private static List<StartStop> getDefaultServices() throws Exception {
        return new ArrayList<>(// need mutable list
                Arrays.asList(
                    new ClientProxy(),
                    // listen at localhost to let dummy proxy listen at 127.0.0.2
                    new ServerProxy("127.0.0.1"),
                    new CertHashBasedOcspResponder("127.0.0.1"),
                    new DummyService(),
                    new DummyServerProxy()));
    }

    private static void startWatchdog() {
        // New timer with daemon thread.
        Timer timer = new Timer(true);

        // Schedule task to kill the test.
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                LOG.error("Test suite is taking too long, exiting");
                System.exit(2);
            }
        }, 20 * 60 * 1000); // 20 minutes.
    }
}
