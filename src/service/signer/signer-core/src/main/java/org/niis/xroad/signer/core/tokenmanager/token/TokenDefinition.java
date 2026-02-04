/*
 * The MIT License
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
package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import org.niis.xroad.signer.core.tokenmanager.module.PrivKeyAttributes;
import org.niis.xroad.signer.core.tokenmanager.module.PubKeyAttributes;

import java.util.Map;
import java.util.Optional;

/**
 * Describes a token type, usually a software or hardware based token.
 */
public interface TokenDefinition {

    /**
     * @return the module type
     */
    String moduleType();

    /**
     * @return true if the token is read only
     */
    boolean readOnly();

    /**
     * @return true if batch signing is enabled for the token
     */
    boolean batchSigningEnabled();

    /**
     * @return true if pin must be verified per signing.
     */
    boolean pinVerificationPerSigning();

    /**
     * @return the slot index of the token
     */
    Integer slotIndex();

    /**
     * @return the serial number of the token
     */
    String serialNumber();

    /**
     * @return the label of the token
     */
    String label();

    /**
     * @return the id of the token
     */
    String getId();

    /**
     * @return the sign mechanism name for algorithm
     */
    Map<KeyAlgorithm, SignMechanism> signMechanisms();

    /**
     * @return the sign mechanism name for algorithm
     */
    default Optional<SignMechanism> resolveSignMechanismName(KeyAlgorithm algorithm) {
        return Optional.ofNullable(signMechanisms().get(algorithm));
    }

    /**
     * @return the private key attributes
     */
    PrivKeyAttributes privKeyAttributes();

    /**
     * @return the public key attributes
     */
    PubKeyAttributes pubKeyAttributes();
}
