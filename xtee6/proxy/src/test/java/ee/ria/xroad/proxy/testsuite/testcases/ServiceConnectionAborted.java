package ee.ria.xroad.proxy.testsuite.testcases;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal message. Service aborts connection during sending
 * of response.
 * Result: SP sends ServiceFailed error.
 */
public class ServiceConnectionAborted extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServiceConnectionAborted() {
        requestFileName = "getstate.query";
    }

    @Override
    public AbstractHandler getServiceHandler() {
        return new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response) {
                // set content length, but send no content
                response.setContentLength(1000);
                baseRequest.setHandled(true);
            }
        };
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_CONTENT_TYPE);
    }
}
