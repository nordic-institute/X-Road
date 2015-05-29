package ee.ria.xroad.proxy.testsuite.testcases;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SOAP;

/**
 * Client sends empty multipart request. CP responds with error.
 * Result: Client.* error.
 */
public class EmptyMultipartRequest extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public EmptyMultipartRequest() {
        requestFileName = "empty.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected InputStream getQueryInputStream(String fileName)
            throws Exception {
        return new ByteArrayInputStream(new byte[] {});
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_MISSING_SOAP);
    }
}
