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
package org.niis.xroad.signer.core.model;

import org.niis.xroad.signer.core.tokenmanager.token.TokenDefinition;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Model object representing a token.
 */
public sealed interface RuntimeToken extends BasicTokenInfo permits RuntimeTokenImpl {

    /**
     * True, if token is available (in module)
     */
    boolean isAvailable();

    /**
     * True, if password is inserted
     */
    boolean isActive();

    /**
     * Holds the current status of the token.
     */
    TokenStatusInfo getStatus();

    /**
     * Contains the keys of this token.
     */
    Collection<RuntimeKey> keys();

    /**
     * Contains label-value pairs of information about token.
     */
    Map<String, String> getTokenInfo();

    /**
     * @return true if token is either not active or not available
     */
    default boolean isInactive() {
        return !isActive() || !isAvailable();
    }

    Optional<TokenDefinition> getTokenDefinition();

}
