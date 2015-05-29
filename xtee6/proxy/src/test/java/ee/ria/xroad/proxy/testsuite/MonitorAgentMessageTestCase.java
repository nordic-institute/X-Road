package ee.ria.xroad.proxy.testsuite;

import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.monitoring.MonitorAgentProvider;

/**
 * Monitor agent message test case.
 */
public class MonitorAgentMessageTestCase extends MessageTestCase {

    protected final TestSuiteMonitorAgent monitorAgent =
            new TestSuiteMonitorAgent();

    @Override
    public void execute() throws Exception {
        MonitorAgent.init(monitorAgent);

        super.execute();

        monitorAgent.verifyAPICalls();

        MonitorAgent.init((MonitorAgentProvider) null); // deinitialize
    }

}
