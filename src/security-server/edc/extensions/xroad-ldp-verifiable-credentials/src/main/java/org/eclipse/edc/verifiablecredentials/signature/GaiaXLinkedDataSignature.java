/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.eclipse.edc.verifiablecredentials.signature;

import com.apicatalog.ld.DocumentError;
import com.apicatalog.ld.signature.CryptoSuite;
import com.apicatalog.ld.signature.LinkedDataSignature;
import com.apicatalog.ld.signature.LinkedDataSuiteError;
import com.apicatalog.ld.signature.SigningError;
import com.apicatalog.ld.signature.VerificationError;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import org.bouncycastle.util.encoders.Hex;

import java.util.Objects;

public class GaiaXLinkedDataSignature extends LinkedDataSignature {

    private final CryptoSuite suite;

    public GaiaXLinkedDataSignature(CryptoSuite suite) {
        super(suite);
        this.suite = suite;
    }

    /**
     * Verifies the given signed VC/VP document.
     *
     * @param expanded        expanded unsigned VC/VP document
     * @param unsignedProof   expanded proof with no proofValue
     * @param verificationKey
     * @param signature
     * @throws VerificationError
     * @throws DocumentError
     * @see <a href=
     * "https://w3c-ccg.github.io/data-integrity-spec/#proof-verification-algorithm">Verification
     * Algorithm</a>
     */
    @Override
    public void verify(
            final JsonObject expanded,
            final JsonObject unsignedProof,
            final byte[] verificationKey,
            final byte[] signature) throws VerificationError {

        Objects.requireNonNull(expanded);
        Objects.requireNonNull(unsignedProof);
        Objects.requireNonNull(verificationKey);
        Objects.requireNonNull(signature);

        try {
            final byte[] computeSignature = hashCode(expanded, unsignedProof);

            suite.verify(verificationKey, signature, computeSignature);

        } catch (LinkedDataSuiteError e) {
            throw new VerificationError(VerificationError.Code.InvalidSignature, e);
        }
    }

    /**
     * Issues the given VC/VP document and returns the document signature.
     *
     * @param expanded   expanded unsigned VC/VP document
     * @param privateKey
     * @param proof
     * @return computed signature
     * @throws SigningError
     * @throws DocumentError
     * @see <A href=
     * "https://w3c-ccg.github.io/data-integrity-spec/#proof-algorithm">Proof
     * Algorithm</a>
     */
    @Override
    public byte[] sign(JsonObject expanded, byte[] privateKey, JsonObject proof) throws SigningError {

        Objects.requireNonNull(expanded);
        Objects.requireNonNull(privateKey);
        Objects.requireNonNull(proof);

        try {
            final byte[] documentHashCode = hashCode(expanded, proof);

            return suite.sign(privateKey, documentHashCode);

        } catch (LinkedDataSuiteError e) {
            throw new SigningError(SigningError.Code.Internal, e);
        }
    }

    /**
     * @param document expanded unsigned VC/VP document
     * @param proof    expanded proof with no proofValue
     * @return computed hash code
     * @throws LinkedDataSuiteError
     * @see <a href=
     * "https://w3c-ccg.github.io/data-integrity-spec/#create-verify-hash-algorithm">Hash
     * Algorithm</a>
     */
    byte[] hashCode(JsonStructure document, JsonObject proof) throws LinkedDataSuiteError {
        // GaiaX signature only takes dacoument hash
        byte[] documentHash = suite.digest(suite.canonicalize(document));

        return Hex.encode(documentHash);
    }

}
