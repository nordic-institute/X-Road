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
import ee.ria.xroad.common.util.EncoderUtils;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.niis.xroad.signer.core.tokenmanager.module.PrivKeyAttributes;
import org.niis.xroad.signer.core.tokenmanager.module.PubKeyAttributes;
import org.niis.xroad.signer.core.util.SignerUtil;

import java.util.Map;
import java.util.Optional;

/**
 * Hardware token type, holding the actual pkcs11 token.
 *
 * @param moduleType                the type of the token module
 * @param tokenIdFormat             the format of the token ID, e.g. "PKCS11"
 * @param token                     the actual PKCS#11 token instance
 * @param readOnly                  whether the token is read-only
 * @param slotIndex                 the index of the slot where the token is located, or null if not applicable
 * @param serialNumber              the serial number of the token
 * @param label                     the label of the token
 * @param pinVerificationPerSigning whether PIN verification is required for each signing operation
 * @param batchSigningEnabled       whether batch signing is enabled for this token
 * @param signMechanisms            a map of key algorithms to their corresponding signing mechanisms
 * @param privKeyAttributes         attributes for private keys on this token
 * @param pubKeyAttributes          attributes for public keys on this token
 */
public record HardwareTokenDefinition(
        String moduleType,
        String tokenIdFormat,
        iaik.pkcs.pkcs11.Token token,
        boolean readOnly,
        Integer slotIndex,
        String serialNumber,
        String label,
        boolean pinVerificationPerSigning,
        boolean batchSigningEnabled,
        Map<KeyAlgorithm, SignMechanism> signMechanisms,
        PrivKeyAttributes privKeyAttributes,
        PubKeyAttributes pubKeyAttributes) implements TokenDefinition {

    @Override
    public String getId() {
        return EncoderUtils.encodeHex(SignerUtil.getFormattedTokenId(tokenIdFormat, moduleType, token).getBytes());
    }

    @Override
    public Optional<SignMechanism> resolveSignMechanismName(KeyAlgorithm algorithm) {
        return Optional.ofNullable(signMechanisms.get(algorithm));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof HardwareTokenDefinition that)) return false;

        return new EqualsBuilder()
                .append(batchSigningEnabled, that.batchSigningEnabled)
                .append(pinVerificationPerSigning, that.pinVerificationPerSigning)
                .append(label, that.label)
                .append(moduleType, that.moduleType)
                .append(slotIndex, that.slotIndex)
                .append(serialNumber, that.serialNumber)
                .append(tokenIdFormat, that.tokenIdFormat)
                .append(token, that.token)
                .append(pubKeyAttributes, that.pubKeyAttributes)
                .append(privKeyAttributes, that.privKeyAttributes)
                .append(signMechanisms, that.signMechanisms)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(moduleType)
                .append(tokenIdFormat)
                .append(token)
                .append(slotIndex)
                .append(serialNumber)
                .append(label)
                .append(pinVerificationPerSigning)
                .append(batchSigningEnabled)
                .append(signMechanisms)
                .append(privKeyAttributes)
                .append(pubKeyAttributes)
                .toHashCode();
    }
}
