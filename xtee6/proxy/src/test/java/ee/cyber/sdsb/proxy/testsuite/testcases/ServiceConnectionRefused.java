package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.monitoring.MessageInfo;
import ee.cyber.sdsb.common.monitoring.MessageInfo.Origin;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MonitorAgentMessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal request, SP receives connection refused when
 * connecting to service.
 * Result: SP responds with ServiceFailed.
 */
public class ServiceConnectionRefused extends MonitorAgentMessageTestCase {

    private final String expectedErrorCode =
            errorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                    X_NETWORK_ERROR);

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
