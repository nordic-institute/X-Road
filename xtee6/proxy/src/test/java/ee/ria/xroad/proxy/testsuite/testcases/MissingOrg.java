package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;

/**
 * A query with an organization that does not exist.
 * Result:
 */
public class MissingOrg extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MissingOrg() {
        requestFileName = "missing-org.query";
        responseFile = "getstate.answer";
    }

    @Override
    public SigningCtx getSigningCtx(String sender) {
        throw new CodedException(X_UNKNOWN_MEMBER);
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_UNKNOWN_MEMBER);
    }
}
