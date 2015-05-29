package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.signature.SignatureBuilder;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;

/**
 * Creating the signature fails in ClientProxy.
 * Result: fault with code X_CANNOT_CREATE_SIGNATURE
 */
public class SigningFailsInClientProxy extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public SigningFailsInClientProxy() {
        requestFileName = "getstate.query";
        responseFile = "getstate.answer";
    }

    @Override
    public SigningCtx getSigningCtx(String sender) {
        return new SigningCtx() {
            @Override
            public SignatureData buildSignature(SignatureBuilder builder) {
                throw new CodedException(X_CANNOT_CREATE_SIGNATURE);
            }
        };
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_CANNOT_CREATE_SIGNATURE);
    }
}
