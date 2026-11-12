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
package org.niis.xroad.ds.identity;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Decides whether the host of a DID is the registered address of a Security Server, and holds the rule that
 * calls a SYSTEM DID with its DSP base URL stale. Pure: no storage, no clock.
 *
 * <p>Hosts are compared after the canonical host rules of {@link DidAuthority}; ports are not compared. A registered
 * address that is unknown, not ASCII or not a usable host, a DID that cannot be decoded, and a DSP base URL that is
 * not an {@code https} URL with a usable host never match, so the value they belong to is stale.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class RegisteredHostMatcher {

    @Nullable
    private final String registeredHost;

    /**
     * @param registeredAddress the address the server is registered under in global configuration; a valid port
     *                          in it is ignored; {@code null} when it is unknown
     * @return the matcher for that address
     */
    public static RegisteredHostMatcher of(@Nullable String registeredAddress) {
        return new RegisteredHostMatcher(DidAuthority.canonicalHostOf(registeredAddress).orElse(null));
    }

    /**
     * @return whether the registered address is known and a usable host, so that a host can match it
     */
    public boolean isRegisteredAddressUsable() {
        return registeredHost != null;
    }

    /**
     * @param did a DID in wire form
     * @return whether the host it names is the registered address
     */
    public boolean didHostMatches(@Nullable String did) {
        return ParticipantIdentifierScheme.tryDecodeDid(did)
                .map(DecodedParticipant::ssHost)
                .filter(this::authorityHostMatches)
                .isPresent();
    }

    /**
     * @param authority a host with an optional port, as in a DID or a DSP base URL
     * @return whether the host it names is the registered address
     */
    public boolean authorityHostMatches(@Nullable String authority) {
        return DidAuthority.canonicalHostOf(authority).filter(host -> host.equals(registeredHost)).isPresent();
    }

    /**
     * @param systemDid  the SYSTEM DID
     * @param dspBaseUrl the DSP base URL
     * @return whether the host of either is not the registered address, or the registered address cannot be used
     */
    public boolean isSystemPairStale(@Nullable String systemDid, @Nullable String dspBaseUrl) {
        return !didHostMatches(systemDid)
                || DidAuthority.httpsAuthority(dspBaseUrl).filter(this::authorityHostMatches).isEmpty();
    }
}
