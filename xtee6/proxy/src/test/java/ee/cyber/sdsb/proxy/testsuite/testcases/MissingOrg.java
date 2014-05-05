package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.proxy.conf.SigningCtx;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_UNKNOWN_MEMBER;

/**
 * A query with an organization that does not exist.
 * Result:
 */
public class MissingOrg extends MessageTestCase {
    public MissingOrg() {
        requestFileName = "missing-org.query";
        responseFileName = "getstate.answer";
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
