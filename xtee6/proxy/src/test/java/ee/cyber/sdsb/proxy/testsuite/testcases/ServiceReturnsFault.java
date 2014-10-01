package ee.cyber.sdsb.proxy.testsuite.testcases;

import java.io.FileInputStream;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

/**
 * Client sends normal message. Service sends fault with 500 error code.
 * Result: SP sends ServiceFailed error.
 */
public class ServiceReturnsFault extends MessageTestCase {
    public ServiceReturnsFault() {
        requestFileName = "getstate.query";
    }

    @Override
    public AbstractHandler getServiceHandler() {
        return new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response)
                        throws IOException {
                response.setContentType(MimeTypes.TEXT_XML);
                response.setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

                IOUtils.copy(new FileInputStream(QUERIES_DIR + "/fault.query"),
                        response.getOutputStream());

                baseRequest.setHandled(true);
            }
        };
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode("CODE");
    }
}
