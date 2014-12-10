package ee.cyber.sdsb.proxy.signedmessage;

import ee.cyber.sdsb.common.signature.MessagePart;
import ee.cyber.sdsb.common.signature.SignatureBuilder;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.proxy.conf.SigningCtx;

/**
 * Encapsulates message signing functionality. This class does not
 * deal with the signed data itself, it is only interested in hashes
 * of the SOAP message and attachments.
 */
public class Signer {

    private SignatureBuilder builder = new SignatureBuilder();

    private SignatureData signature;

    /** Adds new part to be signed.
     * @param name name of the file in the BDOC container.
     * @param data the data.
     */
    public void addPart(String name, String hashMethod, byte[] data) {
        builder.addPart(new MessagePart(name, hashMethod, data));
    }

    /**
     * Signs the hashes and creates the signature.
     */
    public void sign(SigningCtx ctx) throws Exception {
        signature = ctx.buildSignature(builder);
    }

    /**
     * Get the signature data.
     */
    public SignatureData getSignatureData() {
        return signature;
    }

}
