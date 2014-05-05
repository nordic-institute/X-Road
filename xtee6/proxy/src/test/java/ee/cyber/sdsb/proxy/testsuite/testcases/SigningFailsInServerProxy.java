package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.signature.SignatureBuilder;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.proxy.conf.SigningCtx;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;

/**
 * Creating the signature fails in ServerProxy.
 * Result: fault with code X_CANNOT_CREATE_SIGNATURE
 */
public class SigningFailsInServerProxy extends MessageTestCase {
    public SigningFailsInServerProxy() {
        requestFileName = "getstate.query";
        responseFileName = "getstate.answer";
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
