package ee.ria.xroad.proxy.signedmessage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.signature.MessagePart;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.signature.SignatureVerifier;

import static ee.ria.xroad.common.ErrorCodes.X_SIGNATURE_VERIFICATION_X;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;

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
     * @param hashMethod identifier of the algorithm used to calculate the hash.
     * @param data hash value.
     */
    public void addPart(String name, String hashMethod, byte[] data) {
        parts.add(new MessagePart(name, hashMethod, data));
    }

    /**
     * Verify the signature.
     * @param sender client ID of the sender
     * @param signature signature data
     * @throws Exception in case of any errors
     */
    public void verify(ClientId sender, SignatureData signature)
            throws Exception {
        log.trace("Verify, {} parts. Signature: {}", parts.size(), signature);

        if (SystemProperties.IGNORE_SIGNATURE_VERIFICATION) {
            return;
        }

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
