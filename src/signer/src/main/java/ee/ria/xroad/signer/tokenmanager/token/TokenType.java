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

import ee.ria.xroad.signer.tokenmanager.module.PrivKeyAttributes;
import ee.ria.xroad.signer.tokenmanager.module.PubKeyAttributes;

/**
 * Describes a token type, usually a software or hardware based token.
 */
public interface TokenType {

    /**
     * @return the module type
     */
    String getModuleType();

    /**
     * @return true if the token is read only
     */
    boolean isReadOnly();

    /**
     * @return true if batch signing is enabled for the token
     */
    boolean isBatchSigningEnabled();

    /**
     * @return true if pin must be verified per signing.
     */
    boolean isPinVerificationPerSigning();

    /**
     * @return the slot index of the token
     */
    Integer getSlotIndex();

    /**
     * @return the serial number of the token
     */
    String getSerialNumber();

    /**
     * @return the label of the token
     */
    String getLabel();

    /**
     * @return the id of the token
     */
    String getId();

    /**
     * @return the sign mechanism name
     */
    String getSignMechanismName();

    /**
     * @return the private key attributes
     */
    PrivKeyAttributes getPrivKeyAttributes();

    /**
     * @return the public key attributes
     */
    PubKeyAttributes getPubKeyAttributes();
}
