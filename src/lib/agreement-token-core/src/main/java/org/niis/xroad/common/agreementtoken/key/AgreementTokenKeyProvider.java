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
package org.niis.xroad.common.agreementtoken.key;

import java.util.Optional;

/**
 * Owns the agreement-token signing key's lifecycle: provisioning, rotation, and the locally cached read path
 * that {@link org.niis.xroad.common.agreementtoken.AgreementTokenMinter} and
 * {@link org.niis.xroad.common.agreementtoken.AgreementTokenVerifier} use. {@link #activeKey()} and
 * {@link #keyById(String)} read only the in-process cache — never the secret store — so neither minting nor
 * verifying a token ever makes a network call on the request path.
 */
public interface AgreementTokenKeyProvider {

    /**
     * @return the key currently used to sign new tokens
     */
    AgreementTokenSigningKey activeKey();

    /**
     * @param keyId a key id as carried in a token's {@code kid} header
     * @return the matching key, including keys superseded by a later rotation, or empty if unknown
     */
    Optional<AgreementTokenSigningKey> keyById(String keyId);

    /**
     * Generates a new signing key, stores it, and makes it the active key for future minting. Previously
     * issued keys are kept, never deleted, so tokens already minted with them keep verifying until they expire.
     * Operator-driven: nothing in this library calls it on its own.
     *
     * @return the newly created, now-active key
     */
    AgreementTokenSigningKey rotate();

    /**
     * Reloads the local cache from the secret store. Meant to be called off the request path, on a schedule
     * the host application owns (this library does not schedule anything itself).
     */
    void refresh();
}
