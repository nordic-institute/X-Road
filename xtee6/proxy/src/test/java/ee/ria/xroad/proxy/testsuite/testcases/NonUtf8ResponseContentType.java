package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.util.MimeUtils.contentTypeWithCharset;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

/**
 * Client sends normal request, server responds with invalid content type.
 * Result: SP sends error
 */
public class NonUtf8ResponseContentType extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public NonUtf8ResponseContentType() {
        requestFileName = "getstate.query";

        responseFile = "getstate-iso88591.answer";
        responseContentType = TEXT_XML;

        responseServiceContentType =
                contentTypeWithCharset(TEXT_XML, ISO_8859_1.name());

        // Currently the 'getstate.answer' contains different encoding -- should this be an error?
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
    }
}
