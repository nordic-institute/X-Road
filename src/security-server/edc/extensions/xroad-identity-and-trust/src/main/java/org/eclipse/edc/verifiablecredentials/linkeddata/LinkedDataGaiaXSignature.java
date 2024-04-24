package org.eclipse.edc.verifiablecredentials.linkeddata;

import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.signature.CryptoSuite;
import com.apicatalog.ld.signature.KeyGenError;
import com.apicatalog.ld.signature.LinkedDataSuiteError;
import com.apicatalog.ld.signature.SigningError;
import com.apicatalog.ld.signature.VerificationError;
import com.apicatalog.ld.signature.key.KeyPair;
import com.apicatalog.ld.signature.key.VerificationKey;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import org.bouncycastle.util.encoders.Hex;

import java.net.URI;
import java.util.Objects;

public class LinkedDataGaiaXSignature {

    private final CryptoSuite suite;

    public LinkedDataGaiaXSignature(CryptoSuite suite) {
        this.suite = suite;
    }

    /**
     * Verifies the given signed VC/VP document.
     *
     * @see <a href=
     *      "https://w3c-ccg.github.io/data-integrity-spec/#proof-verification-algorithm">Verification
     *      Algorithm</a>
     *
     * @param document        expanded unsigned VC/VP document
     * @param verificationKey
     * @param signature
     *
     * @throws VerificationError
     * @throws DocumentError
     */
    public void verify(
            final JsonObject document,
            final VerificationKey verificationKey,
            final byte[] signature) throws VerificationError {

        Objects.requireNonNull(verificationKey);
        Objects.requireNonNull(verificationKey.publicKey());
        Objects.requireNonNull(signature);

        try {
            final byte[] computeSignature = hashCode(document);

            suite.verify(verificationKey.publicKey(), signature, computeSignature);

        } catch (LinkedDataSuiteError e) {
            throw new VerificationError(VerificationError.Code.InvalidSignature, e);
        }
    }

    /**
     * Issues the given VC/VP document and returns the document signature.
     *
     * @see <A href=
     *      "https://w3c-ccg.github.io/data-integrity-spec/#proof-algorithm">Proof
     *      Algorithm</a>
     *
     * @param document expanded unsigned VC/VP document
     * @param keyPair
     *
     * @return computed signature
     *
     * @throws SigningError
     * @throws DocumentError
     */
    public byte[] sign(JsonObject document, KeyPair keyPair) throws SigningError {

        try {
            final byte[] documentHashCode = hashCode(document);

            return suite.sign(keyPair.privateKey(), documentHashCode);

        } catch (LinkedDataSuiteError e) {
            throw new SigningError(SigningError.Code.Internal, e);
        }
    }

    /**
     * @see <a href=
     *      "https://w3c-ccg.github.io/data-integrity-spec/#create-verify-hash-algorithm">Hash
     *      Algorithm</a>
     *
     * @param document expanded unsigned VC/VP document
     *
     * @return computed hash code
     *
     * @throws LinkedDataSuiteError
     */
    byte[] hashCode(JsonStructure document) throws LinkedDataSuiteError {
        byte[] documentHash = suite.digest(suite.canonicalize(document));

        return Hex.encode(documentHash);
    }

    public KeyPair keygen(URI id, int length) throws KeyGenError {
        return suite.keygen(length);
    }

}
