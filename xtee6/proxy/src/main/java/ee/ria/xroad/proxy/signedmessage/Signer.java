package ee.ria.xroad.proxy.signedmessage;

import ee.ria.xroad.common.signature.MessagePart;
import ee.ria.xroad.common.signature.SignatureBuilder;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.proxy.conf.SigningCtx;

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
     * @param hashMethod identifier of the algorithm used to calculate the hash
     * @param data the data.
     */
    public void addPart(String name, String hashMethod, byte[] data) {
        builder.addPart(new MessagePart(name, hashMethod, data));
    }

    /**
     * Signs the hashes and creates the signature.
     * @param ctx signing context used for signing
     * @throws Exception in case of any errors
     */
    public void sign(SigningCtx ctx) throws Exception {
        signature = ctx.buildSignature(builder);
    }

    /**
     * @return the signature data
     */
    public SignatureData getSignatureData() {
        return signature;
    }

}
