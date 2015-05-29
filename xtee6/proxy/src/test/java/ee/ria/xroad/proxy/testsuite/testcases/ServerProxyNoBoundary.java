package ee.ria.xroad.proxy.testsuite.testcases;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;

/**
 * Client sends normal message, SP aborts connection
 * (content type: multipart/mixed).
 * Result: CP responds with RequestFailed
 */
public class ServerProxyNoBoundary extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServerProxyNoBoundary() {
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

                response.setContentType("multipart/mixed");
                response.setContentLength(1000);
                response.setHeader(HEADER_HASH_ALGO_ID,
                        DEFAULT_DIGEST_ALGORITHM_ID);
                baseRequest.setHandled(true);
            }
        };
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_CONTENT_TYPE);
    }
}
