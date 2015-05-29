package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MessageInfo.Origin;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MonitorAgentMessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal request, SP receives connection refused when
 * connecting to service.
 * Result: SP responds with ServiceFailed.
 */
public class ServiceConnectionRefused extends MonitorAgentMessageTestCase {

    private final String expectedErrorCode =
            errorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                    X_NETWORK_ERROR);

    /**
     * Constructs the test case.
     */
    public ServiceConnectionRefused() {
        requestFileName = "getstate.query";

        monitorAgent.expectFailure(
                new MessageInfo(Origin.SERVER_PROXY,
                        ClientId.create("EE", "BUSINESS", "consumer"),
                        ServiceId.create("EE", "BUSINESS", "producer", null,
                                "getState"), null, null), expectedErrorCode);
        monitorAgent.expectFailure(
                new MessageInfo(Origin.CLIENT_PROXY,
                        ClientId.create("EE", "BUSINESS", "consumer"),
                        ServiceId.create("EE", "BUSINESS", "producer", null,
                                "getState"), null, null), expectedErrorCode);
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return "http://127.0.0.5:8989/";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(expectedErrorCode);
    }

}
