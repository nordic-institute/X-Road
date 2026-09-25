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

import com.apicatalog.did.Did;
import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.net.URI;
import java.net.URISyntaxException;

import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

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
     * Suffix appended to the host participant context id for the legacy management companion
     * context. Applies to context ids only; the DID form is {@link ParticipantIdentifierScheme#managementDid}.
     * Interim: dies with the SYSTEM-context migration.
     */
    public static final String MANAGEMENT_CONTEXT_SUFFIX = "-mgmt";
    /**
     * DID-document service type under which a participant's DCP credential service is published.
     */
    public static final String CREDENTIAL_SERVICE_TYPE = "CredentialService";

    private static final String CREDENTIAL_SERVICE_ID_SUFFIX = "-credential-service";
    @SuppressWarnings("java:S1075")
    private static final String CREDENTIALS_API_PARTICIPANTS_PATH = "/api/credentials/v1/participants/";

    /**
     * The {@code host:port} authority under which a Security Server's participant DIDs are minted
     * and their DID documents served, at the ecosystem-wide {@link #DID_PORT}. This is the form
     * every counter-party derives from GlobalConf alone.
     *
     * @param ssAddress the Security Server's GlobalConf-registered address, without a port
     * @return the DID authority, {@code {ssAddress}:7183}; an IPv6 literal address is bracketed
     */
    public static String didAuthority(String ssAddress) {
        return didAuthority(ssAddress, DID_PORT);
    }

    /**
     * {@link #didAuthority(String)} at the given DID port. A provider minting its own DIDs uses its
     * configured identity hub DID port here; counter-parties can only derive
     * {@link #didAuthority(String)}, so the two agree only while the configured port is
     * {@link #DID_PORT}.
     *
     * @param ssAddress the Security Server's GlobalConf-registered address, without a port
     * @param didPort   the identity hub's DID resolution port
     * @return the DID authority, {@code {ssAddress}:{didPort}}; an IPv6 literal address is bracketed
     */
    public static String didAuthority(String ssAddress, int didPort) {
        return uriHost(ssAddress) + ":" + didPort;
    }

    /**
     * The counter-party id (URL-encoded {@code did:web} DID) of a member participant served at the
     * given Security Server address.
     *
     * @param member    the provider member; must not carry a subsystem code
     * @param ssAddress the serving Security Server's GlobalConf-registered address, without a port
     * @return the member's per-server DID, e.g. {@code did:web:ss0.example.org%3A7183:v1:DEV:COM:222}
     */
    public static Did memberCounterPartyId(ClientId member, String ssAddress) {
        return ParticipantIdentifierScheme.memberDid(member, didAuthority(ssAddress));
    }

    /**
     * The counter-party address (DSP base URL of the serving control plane, scoped to the member's
     * participant context and the DSP profile) of a member participant served at the given Security
     * Server address.
     *
     * <p>The context id is embedded as a percent-encoded path segment: a ctx-id may itself contain
     * literal {@code %} (from {@code enc()}-escaped member codes), and the provider's JAX-RS layer
     * decodes the segment once, so the {@code %} must ride the wire as {@code %25} to decode back
     * to the registered context id. {@code :} needs no escaping — it is a legal pchar.
     *
     * @param member    the provider member; must not carry a subsystem code
     * @param ssAddress the serving Security Server's GlobalConf-registered address, without a port
     * @return the full DSP base URL, e.g. {@code https://ss0.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1};
     *         an IPv6 literal address is bracketed
     */
    public static String memberCounterPartyAddress(ClientId member, String ssAddress) {
        var ctxIdPathSegment = ParticipantIdentifierScheme.memberCtxId(member).replace("%", "%25");
        return "https://%s:%d/api/dsp/%s/%s"
                .formatted(uriHost(ssAddress), DSP_PORT, ctxIdPathSegment, DSP_PROFILE_ID);
    }

    /**
     * The id of a participant's credential-service entry in its DID document.
     *
     * @param participantContextId the participant context id
     * @return the service id, {@code {ctx-id}-credential-service}
     */
    public static String credentialServiceId(String participantContextId) {
        return participantContextId + CREDENTIAL_SERVICE_ID_SUFFIX;
    }

    /**
     * The endpoint of a participant's credential-service entry: the identity hub's DCP credentials
     * API scoped to the participant context.
     *
     * @param identityHubHost      host the identity hub is reached at; an IPv6 literal may be bare or bracketed
     * @param credentialsPort      the identity hub's credentials API port
     * @param participantContextId the participant context id, percent-encoded as one path segment
     * @return the credential-service URL
     */
    public static String credentialServiceUrl(String identityHubHost, int credentialsPort, String participantContextId) {
        try {
            return new URI("https", null, uriHost(identityHubHost), credentialsPort,
                    CREDENTIALS_API_PARTICIPANTS_PATH + participantContextId, null, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR, e,
                    "cannot build a credential-service URL for host '%s' and participant '%s'", identityHubHost, participantContextId);
        }
    }

    /**
     * A registered address is a hostname, an IPv4 literal, or a bare IPv6 literal — never
     * {@code host:port} — so a colon can only mean IPv6, which URL authorities require bracketed.
     */
    private static String uriHost(String ssAddress) {
        boolean bareIpv6Literal = ssAddress.indexOf(':') >= 0 && !ssAddress.startsWith("[");
        return bareIpv6Literal ? "[" + ssAddress + "]" : ssAddress;
    }
}
