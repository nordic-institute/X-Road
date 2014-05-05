package ee.cyber.sdsb.proxy.testsuite.testcases;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal message, SP aborts connection (content type: text/xml).
 * Result: CP responds with RequestFailed
 */
public class ServerProxyConnectionAborted2 extends MessageTestCase {
    public ServerProxyConnectionAborted2() {
        requestFileName = "getstate.query";
    }

    @Override
    public String getProviderAddress(String providerName) {
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

                response.setContentType("text/xml");
                response.setContentLength(1000);
                baseRequest.setHandled(true);
            }
        };
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_SERVICE_FAILED_X, X_IO_ERROR);
    }
}
