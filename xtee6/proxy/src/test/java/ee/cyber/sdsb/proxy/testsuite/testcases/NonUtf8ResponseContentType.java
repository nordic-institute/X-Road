package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.util.MimeUtils.contentTypeWithCharset;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

/**
 * Client sends normal request, server responds with invalid content type.
 * Result: SP sends error
 */
public class NonUtf8ResponseContentType extends MessageTestCase {
    public NonUtf8ResponseContentType() {
        requestFileName = "getstate.query";

        responseFileName = "getstate.answer";
        responseContentType = contentTypeWithCharset(TEXT_XML,
                ISO_8859_1.name());

        // Currently the 'getstate.answer' contains different encoding -- should this be an error?
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
    }
}
