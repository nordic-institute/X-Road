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
package org.niis.xroad.ds.identity;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.experimental.UtilityClass;

/**
 * Ecosystem-wide DSP conventions that are not distributed via GlobalConf: the fixed ports of the
 * identity hub's DID endpoint and the control plane's DSP endpoint, the DSP profile id, and the
 * counter-party coordinates every consumer derives from them.
 *
 * <p>A member served by several Security Servers is a distinct participant on each (per XRDADR-41),
 * so counter-party coordinates are derived per serving server address; selecting among the serving
 * servers is the caller's concern.
 */
@UtilityClass
public class DspConventions {

    /**
     * Port of the identity hub's DID resolution endpoint, part of every derived {@code did:web} authority.
     */
    public static final int DID_PORT = 7183;

    /**
     * Port of the control plane's DSP protocol endpoint, part of every derived counter-party address.
     */
    public static final int DSP_PORT = 8183;

    /**
     * DSP profile id segment of the provider DSP URL, and the profile registered by the
     * EDC virtual runtime ({@code DspVirtualApiConfigurationV2025Extension}). Since EDC 0.18
     * the profile id replaces the bare protocol version ({@code 2025-1}) as the URL segment.
     */
    public static final String DSP_PROFILE_ID = "http-dsp-profile-2025-1";

    /**
     * Suffix appended to the host participant context id (and its DID) for the legacy management
     * companion context. Interim: dies with the SYSTEM-context migration.
     */
    public static final String MANAGEMENT_CONTEXT_SUFFIX = "-mgmt";

    /**
     * The {@code host:port} authority under which a Security Server's participant DIDs are minted
     * and their DID documents served.
     *
     * @param ssAddress the Security Server's GlobalConf-registered address, without a port
     * @return the DID authority, {@code {ssAddress}:7183}
     */
    public static String didAuthority(String ssAddress) {
        return ssAddress + ":" + DID_PORT;
    }

    /**
     * The interim MVP DID of a Security Server's HOST participant context: the bare authority as a
     * {@code did:web}, no scheme version. Interim: dies with the SYSTEM-context migration.
     *
     * @param ssAddress the Security Server's GlobalConf-registered address, without a port
     * @return the host context DID, e.g. {@code did:web:ss0.example.org%3A7183}
     */
    public static String hostDid(String ssAddress) {
        return "did:web:" + didAuthority(ssAddress).replace(":", "%3A");
    }

    /**
     * The interim MVP DID of a Security Server's management companion context: the host DID plus the
     * {@code :mgmt} path segment. Interim: dies with the SYSTEM-context migration.
     *
     * @param ssAddress the Security Server's GlobalConf-registered address, without a port
     * @return the management context DID, e.g. {@code did:web:ss0.example.org%3A7183:mgmt}
     */
    public static String managementDid(String ssAddress) {
        return hostDid(ssAddress) + ":mgmt";
    }

    /**
     * The counter-party id (URL-encoded {@code did:web} DID) of a member participant served at the
     * given Security Server address.
     *
     * @param member    the provider member; must not carry a subsystem code
     * @param ssAddress the serving Security Server's GlobalConf-registered address, without a port
     * @return the member's per-server DID, e.g. {@code did:web:ss0.example.org%3A7183:v1:DEV:COM:222}
     */
    public static String memberCounterPartyId(ClientId member, String ssAddress) {
        return ParticipantIdentifierScheme.memberDid(member, didAuthority(ssAddress));
    }

    /**
     * The counter-party address (DSP base URL of the serving control plane, scoped to the member's
     * participant context and the DSP profile) of a member participant served at the given Security
     * Server address.
     *
     * @param member    the provider member; must not carry a subsystem code
     * @param ssAddress the serving Security Server's GlobalConf-registered address, without a port
     * @return the full DSP base URL, e.g. {@code https://ss0.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1}
     */
    public static String memberCounterPartyAddress(ClientId member, String ssAddress) {
        return "https://%s:%d/api/dsp/%s/%s"
                .formatted(ssAddress, DSP_PORT, ParticipantIdentifierScheme.memberCtxId(member), DSP_PROFILE_ID);
    }
}
