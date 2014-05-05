package ee.cyber.sdsb.proxy.testsuite;

import ee.cyber.sdsb.common.monitoring.MonitorAgent;
import ee.cyber.sdsb.common.monitoring.MonitorAgentProvider;

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
