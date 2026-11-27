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

import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.net.URI;
import java.net.URISyntaxException;

import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * Ecosystem-wide DSP conventions that are not distributed via GlobalConf: the DSP profile and path, and the
 * builders of DID authorities, DSP URLs and credential-service endpoints from published or configured values.
 *
 * <p>A member served by several Security Servers is a distinct participant on each (per XRDADR-41);
 * selecting among the serving servers is the caller's concern.
 */
@UtilityClass
public class DspConventions {

    /**
     * Path of the control plane's DSP protocol endpoint, the tail of every DSP base URL.
     */
    public static final String DSP_PATH = "/api/dsp";

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    /**
     * DSP profile id segment of the provider DSP URL, and the profile registered by the
     * EDC virtual runtime ({@code DspVirtualApiConfigurationV2025Extension}). Since EDC 0.18
     * the profile id replaces the bare protocol version ({@code 2025-1}) as the URL segment.
     */
    public static final String DSP_PROFILE_ID = "http-dsp-profile-2025-1";

    /**
     * Property of the provider's data address that carries {@code true} when the provider has a grant for the
     * agreement but the caller's live ACL lists no endpoint, so the provider refuses access. The consumer treats such
     * an address as a refused transfer instead of sending a tokenless call.
     */
    public static final String ACCESS_REFUSED_PROPERTY = "https://w3id.org/edc/v0.0.1/ns/accessRefused";

    /**
     * DID-document service type under which a participant's DCP credential service is published.
     */
    public static final String CREDENTIAL_SERVICE_TYPE = "CredentialService";

    private static final String CREDENTIAL_SERVICE_ID_SUFFIX = "-credential-service";
    @SuppressWarnings("java:S1075")
    private static final String CREDENTIALS_API_PARTICIPANTS_PATH = "/api/credentials/v1/participants/";

    /**
     * The {@code host:port} authority under which a Security Server's participant DIDs are minted
     * and their DID documents served.
     *
     * @param ssAddress the Security Server's address, without a port
     * @param didPort   the identity hub's DID resolution port
     * @return the canonical DID authority, {@code {ssAddress}:{didPort}}; an IPv6 literal address is
     *         bracketed and port 443 is omitted
     * @throws XrdRuntimeException as {@link DidAuthority#canonicalHost(String)}
     */
    public static String didAuthority(String ssAddress, int didPort) {
        return DidAuthority.canonical(ssAddress, didPort);
    }

    /**
     * The DSP base URL of a Security Server's control plane: {@code https://{host}:{dspPort}/api/dsp}.
     * The raw address is checked before it is canonicalised.
     *
     * @param ssAddress the Security Server's GlobalConf-registered address, without a port; must be ASCII
     * @param dspPort   the control plane's DSP port, 1 to 65535
     * @return the DSP base URL, e.g. {@code https://ss0.example.org:8183/api/dsp}; an IPv6 literal
     *         address is bracketed
     * @throws XrdRuntimeException if the address is blank, not ASCII or not a valid host name or IPv6 literal, or
     *         the port is out of range
     */
    public static String dspBaseUrl(String ssAddress, int dspPort) {
        if (ssAddress == null || ssAddress.isBlank()) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR, "DSP address must not be blank");
        }
        DidAuthority.requireAscii(ssAddress);
        if (dspPort < MIN_PORT || dspPort > MAX_PORT) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "DSP port '%d' must be between %d and %d", dspPort, MIN_PORT, MAX_PORT);
        }
        return "https://%s:%d%s".formatted(DidAuthority.canonicalHost(ssAddress), dspPort, DSP_PATH);
    }

    /**
     * Encodes a participant context id, as {@link ParticipantIdentifierScheme} produces it, for use as one URL path
     * segment. A context id may itself contain a literal
     * {@code %} (from {@code enc()}-escaped member codes) and the receiving JAX-RS layer decodes the segment once, so
     * the {@code %} must travel as {@code %25} to decode back to the registered context id. Nothing else is encoded:
     * the context-id scheme emits unreserved characters, {@code :} and {@code %XX} triplets only, and {@code :} is a
     * legal path-segment character.
     *
     * @param participantContextId a context id from {@link ParticipantIdentifierScheme}; must not be null
     * @return the id with every {@code %} replaced by {@code %25}
     */
    public static String participantPathSegment(String participantContextId) {
        return participantContextId.replace("%", "%25");
    }

    /**
     * The counter-party address of a participant that published its DSP base URL: the published base URL, the
     * participant's context segment taken from its DID, and the DSP profile.
     *
     * @param dspBaseUrl  the published DSP base URL of the participant's control plane; a trailing {@code /} is dropped
     * @param participant the participant decoded from its published DID, member or SYSTEM
     * @return the full DSP base URL, e.g. {@code https://ss0.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1}
     */
    public static String dspTarget(String dspBaseUrl, DecodedParticipant participant) {
        var contextId = switch (participant) {
            case MemberParticipant member -> ParticipantIdentifierScheme.memberCtxId(member.member());
            case SystemParticipant _ -> ParticipantIdentifierScheme.SYSTEM_SEGMENT;
        };
        var baseUrl = dspBaseUrl.endsWith("/") ? dspBaseUrl.substring(0, dspBaseUrl.length() - 1) : dspBaseUrl;
        return "%s/%s/%s".formatted(baseUrl, participantPathSegment(contextId), DSP_PROFILE_ID);
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
     * @param participantContextId the raw participant context id; the URI constructor percent-encodes it as one path
     *                             segment, which yields the same segment as {@link #participantPathSegment(String)}
     * @return the credential-service URL
     */
    public static String credentialServiceUrl(String identityHubHost, int credentialsPort, String participantContextId) {
        try {
            return new URI("https", null, DidAuthority.canonicalHost(identityHubHost), credentialsPort,
                    CREDENTIALS_API_PARTICIPANTS_PATH + participantContextId, null, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR, e,
                    "cannot build a credential-service URL for host '%s' and participant '%s'", identityHubHost, participantContextId);
        }
    }
}
