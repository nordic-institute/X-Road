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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.niis.xroad.signer.core.tokenmanager.module.PrivKeyAttributes;
import org.niis.xroad.signer.core.tokenmanager.module.PubKeyAttributes;
import org.niis.xroad.signer.core.util.SignerUtil;

import java.util.Map;
import java.util.Optional;

/**
 * Hardware token type, holding the actual pkcs11 token.
 */
@Value
@EqualsAndHashCode(exclude = "readOnly")
public class HardwareTokenType implements TokenType {

    String moduleType;

    String tokenIdFormat;

    iaik.pkcs.pkcs11.Token token;

    boolean readOnly;

    Integer slotIndex;

    String serialNumber;

    String label;

    boolean pinVerificationPerSigning;

    boolean batchSigningEnabled;

    Map<KeyAlgorithm, SignMechanism> signMechanisms;

    PrivKeyAttributes privKeyAttributes;

    PubKeyAttributes pubKeyAttributes;

    @Override
    public String getId() {
        return EncoderUtils.encodeHex(SignerUtil.getFormattedTokenId(tokenIdFormat, moduleType, token).getBytes());
    }

    @Override
    public Optional<SignMechanism> resolveSignMechanismName(KeyAlgorithm algorithm) {
        return Optional.ofNullable(signMechanisms.get(algorithm));
    }

}
