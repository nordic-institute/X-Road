package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.signature.SignatureBuilder;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;

/**
 * Creating the signature fails in ServerProxy.
 * Result: fault with code X_CANNOT_CREATE_SIGNATURE
 */
public class SigningFailsInServerProxy extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public SigningFailsInServerProxy() {
        requestFileName = "getstate.query";
        responseFile = "getstate.answer";
    }

    @Override
    public SigningCtx getSigningCtx(String sender) {
        if (sender.equals("producer")) {
            return new SigningCtx() {
                @Override
                public SignatureData buildSignature(SignatureBuilder builder) {
                    throw new CodedException(X_CANNOT_CREATE_SIGNATURE);
                }
            };
        } else {
            return super.getSigningCtx(sender);
        }
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_CANNOT_CREATE_SIGNATURE);
    }
}
