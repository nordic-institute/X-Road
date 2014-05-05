package ee.cyber.sdsb.proxy.testsuite.testcases;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal request. Service responds with HTTP error code.
 * Result: SP responds with ServiceFailed.
 */
public class ServiceHttpError extends MessageTestCase {
    public ServiceHttpError() {
        requestFileName = "getstate.query";
    }

    @Override
    public AbstractHandler getServiceHandler() {
        return new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response)
                    throws IOException {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                baseRequest.setHandled(true);
            }
        };
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X, X_HTTP_ERROR);
    }
}
