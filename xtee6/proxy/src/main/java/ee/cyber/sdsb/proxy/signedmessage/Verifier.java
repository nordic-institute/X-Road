package ee.cyber.sdsb.proxy.signedmessage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.signature.MessagePart;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.signature.SignatureVerifier;

import static ee.cyber.sdsb.common.ErrorCodes.X_SIGNATURE_VERIFICATION_X;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;

/**
 * Encapsulates message verification functionality. This class does not
 * deal with the signed data itself, it is only interested in hashes
 * of the SOAP message and attachments.
 */
@Slf4j
public class Verifier {

    private final List<MessagePart> parts = new ArrayList<>();

    /** Adds new hash to be verified.
     * @param name name of the file in the BDOC container.
     * @param data hash value.
     */
    public void addPart(String name, String hashMethod, byte[] data) {
        parts.add(new MessagePart(name, hashMethod, data));
    }

    /** Verify the signature. */
    public void verify(ClientId sender, SignatureData signature)
            throws Exception {
        log.trace("Verify, {} parts. Signature: {}", parts.size(), signature);
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
