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
package ee.ria.xroad.signer.tokenmanager.token;

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.tokenmanager.module.PrivKeyAttributes;
import ee.ria.xroad.signer.tokenmanager.module.PubKeyAttributes;
import ee.ria.xroad.signer.util.SignerUtil;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Hardware token type, holding the actual pkcs11 token.
 */
@Value
@EqualsAndHashCode(exclude = "readOnly")
public class HardwareTokenType implements TokenType {

    private final String moduleType;

    private final String tokenIdFormat;

    private final iaik.pkcs.pkcs11.Token token;

    private final boolean readOnly;

    private final Integer slotIndex;

    private final String serialNumber;

    private final String label;

    private boolean pinVerificationPerSigning;

    private boolean batchSigningEnabled;

    private final String signMechanismName;

    private final PrivKeyAttributes privKeyAttributes;

    private final PubKeyAttributes pubKeyAttributes;

    @Override
    public String getId() {
        return CryptoUtils.encodeHex(SignerUtil.getFormattedTokenId(tokenIdFormat, moduleType, token).getBytes());
    }

}
