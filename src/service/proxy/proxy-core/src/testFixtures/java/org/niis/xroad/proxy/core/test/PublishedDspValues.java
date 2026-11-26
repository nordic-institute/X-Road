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
package org.niis.xroad.proxy.core.test;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.experimental.UtilityClass;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.model.ServerSystemValues;

import java.util.Optional;
import java.util.Set;

/**
 * The values a provider Security Server publishes in the test global configuration for tests that exercise the
 * negotiated call: one server per provider member, a member DID in the {@code v1} scheme and an {@code https} DSP
 * base URL.
 */
@UtilityClass
public class PublishedDspValues {
    private static final String PUBLISHED_ADDRESS = "ss-a.example";
    private static final int DID_PORT = Integer.parseInt(DspConfigKeys.DID_PORT.defaultValue());
    private static final int DSP_PORT = Integer.parseInt(DspConfigKeys.DSP_PORT.defaultValue());
    private static final String SERVER_CODE = "SS1";

    /**
     * @param provider the provider member or subsystem
     * @return the provider's published server, owned by the provider's member
     */
    public static Set<SecurityServerId> serversOf(ClientId provider) {
        return Set.of(SecurityServerId.Conf.create(memberOf(provider), SERVER_CODE));
    }

    /**
     * @param provider the provider member or subsystem
     * @return the member DID the provider's server publishes
     */
    public static Optional<String> memberDid(ClientId provider) {
        return Optional.of(memberDidAt(memberOf(provider), PUBLISHED_ADDRESS));
    }

    /**
     * @return the SYSTEM DID and DSP base URL the published servers publish
     */
    public static Optional<ServerSystemValues> systemValues() {
        return Optional.of(new ServerSystemValues(systemDidAt(PUBLISHED_ADDRESS),
                DspConventions.dspBaseUrl(PUBLISHED_ADDRESS, DSP_PORT)));
    }

    /**
     * @param member the member
     * @param host   the host of the server that publishes the DID
     * @return the member DID of a server at the host, in the {@code v1} scheme
     */
    public static String memberDidAt(ClientId member, String host) {
        return ParticipantIdentifierScheme.memberDid(member, DspConventions.didAuthority(host, DID_PORT)).toString();
    }

    /**
     * @param host the host of the server that publishes the DID
     * @return the SYSTEM DID of a server at the host, in the {@code v1} scheme
     */
    public static String systemDidAt(String host) {
        return ParticipantIdentifierScheme.systemDid(DspConventions.didAuthority(host, DID_PORT)).toString();
    }

    private static ClientId memberOf(ClientId provider) {
        return ClientId.Conf.create(provider.getXRoadInstance(), provider.getMemberClass(), provider.getMemberCode());
    }
}
