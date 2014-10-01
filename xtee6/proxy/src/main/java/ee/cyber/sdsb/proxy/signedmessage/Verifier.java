package ee.cyber.sdsb.proxy.signedmessage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.signature.MessagePart;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.signature.SignatureVerifier;

import static ee.cyber.sdsb.common.ErrorCodes.X_SIGNATURE_VERIFICATION_X;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;
import static ee.cyber.sdsb.common.util.CryptoUtils.encodeBase64;

/**
 * Encapsulates message verification functionality. This class does not
 * deal with the signed data itself, it is only interested in hashes
 * of the SOAP message and attachments.
 */
public class Verifier {

    private static final Logger LOG = LoggerFactory.getLogger(Verifier.class);

    private final List<MessagePart> parts = new ArrayList<>();

    /** Adds new hash to be verified.
     * @param name name of the file in the BDOC container.
     * @param data hash value.
     */
    public void addPart(String name, String hashMethod, byte[] data) {
        parts.add(new MessagePart(name, hashMethod, encodeBase64(data)));
    }

    /** Verify the signature. */
    public void verify(ClientId sender, SignatureData signature)
            throws Exception {
        LOG.trace("Verify, {} parts. Signature: {}", parts.size(), signature);
        try {
            SignatureVerifier signatureVerifier =
                    new SignatureVerifier(signature);

            signatureVerifier.addParts(parts);

            signatureVerifier.verify(sender, new Date());
        } catch (Exception ex) {
            throw translateWithPrefix(X_SIGNATURE_VERIFICATION_X, ex);
        }
    }

}
