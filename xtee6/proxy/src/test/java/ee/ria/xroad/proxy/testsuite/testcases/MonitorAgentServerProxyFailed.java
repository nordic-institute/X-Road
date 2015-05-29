package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MessageInfo.Origin;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MonitorAgentMessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;

/**
 * Client sends normal message. The CP gets connection refused error
 * when connecting to SP.
 * Result: CP responds with error
 */
public class MonitorAgentServerProxyFailed extends MonitorAgentMessageTestCase {

    /**
     * Constructs the test case.
     */
    public MonitorAgentServerProxyFailed() {
        requestFileName = "getstate.query";

        monitorAgent.expectServerProxyFailed(
                new MessageInfo(Origin.CLIENT_PROXY,
                        ClientId.create("EE", "BUSINESS", "consumer"),
                        ServiceId.create("EE", "BUSINESS", "producer", null,
                                "getState"), null, null));

        monitorAgent.expectFailure(
                new MessageInfo(Origin.CLIENT_PROXY,
                        ClientId.create("EE", "BUSINESS", "consumer"),
                        ServiceId.create("EE", "BUSINESS", "producer", null,
                                "getState"), null, null),
                errorCode(SERVER_CLIENTPROXY_X, X_NETWORK_ERROR));
    }

    @Override
    public String getProviderAddress(String providerName) {
        // Nobody listens to port 5555 on this address.
        return "127.0.0.3";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_NETWORK_ERROR);
    }
}
