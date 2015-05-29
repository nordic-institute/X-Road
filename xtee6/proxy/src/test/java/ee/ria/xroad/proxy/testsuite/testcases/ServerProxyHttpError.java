package ee.ria.xroad.proxy.testsuite.testcases;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_HTTP_ERROR;

/**
 * Server proxy responds with HTTP error.
 * Result: clientproxy generates error message.
 */
public class ServerProxyHttpError extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServerProxyHttpError() {
        requestFileName = "getstate.query";
    }

    @Override
    public String getProviderAddress(String providerName) {
        // Connect to dummy server proxy.
        return "127.0.0.2";
    }

    @Override
    public AbstractHandler getServerProxyHandler() {
        return new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response)
                    throws IOException {
                // Read all of the request.
                IOUtils.readLines(request.getInputStream());

                response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
                baseRequest.setHandled(true);
            }
        };
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_HTTP_ERROR);
    }
}
