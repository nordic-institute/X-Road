package ee.cyber.sdsb.proxy.testsuite.testcases;

import java.io.File;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal message. SP responds with fault message
 * (content type is text/xml instead of multipart) that is empty.
 * Result: CP responds with error.
 */
public class ServerProxyEmptyFault extends MessageTestCase {
    public ServerProxyEmptyFault() {
        requestFileName = "getstate.query";

        responseFileName = "empty.query";
    }

    @Override
    public String getProviderAddress(String providerName) {
        return "127.0.0.2";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_SOAP);
    }
}
