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

package org.niis.xroad.ds.issuance.membership;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.identityhub.protocols.dcp.spi.DcpHolderTokenVerifier;
import org.eclipse.edc.identityhub.protocols.dcp.spi.model.DcpRequestContext;
import org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Decorator over EDC's {@link DcpHolderTokenVerifier} that propagates the validated JWT's
 * claims into the {@link DcpRequestContext#claims()} map under the {@code "jwt"} key.
 *
 * <p>The stock {@code DcpHolderTokenVerifierImpl} validates the holder token but discards
 * the parsed {@link ClaimToken}, returning a {@link DcpRequestContext} with an empty claims
 * map. That makes {@code AttestationContext.getClaimToken("jwt")} return {@code null}
 * downstream, which prevents {@link XRoadMembershipAttestationSource} from reaching the
 * {@code xroadMemberClaim} embedded in the request token.
 *
 * <p>We re-parse the JWT payload (already verified upstream — signature, audience, issuer
 * key, expiry) using a base64url+JSON decode, build a {@link ClaimToken}, and merge into
 * the request context.
 */
public class XRoadClaimPropagatingDcpHolderTokenVerifier implements DcpHolderTokenVerifier {

    static final String JWT_CLAIM_TYPE = "jwt";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {
    };

    private final DcpHolderTokenVerifier delegate;
    private final Monitor monitor;

    public XRoadClaimPropagatingDcpHolderTokenVerifier(DcpHolderTokenVerifier delegate, Monitor monitor) {
        this.delegate = delegate;
        this.monitor = monitor;
    }

    @Override
    public ServiceResult<DcpRequestContext> verify(IdentityHubParticipantContext issuerContext,
                                                   TokenRepresentation token) {
        ServiceResult<DcpRequestContext> upstream = delegate.verify(issuerContext, token);
        if (upstream.failed()) {
            monitor.warning("X-Road DCP holder-token verification rejected for issuer context '%s': %s"
                    .formatted(issuerContext.getParticipantContextId(), upstream.getFailureDetail()));
            return upstream;
        }
        DcpRequestContext context = upstream.getContent();
        ServiceResult<ClaimToken> parsed = parseClaims(token.getToken());
        if (parsed.failed()) {
            return parsed.mapFailure();
        }
        Map<String, ClaimToken> claims = new HashMap<>(context.claims());
        claims.put(JWT_CLAIM_TYPE, parsed.getContent());
        return ServiceResult.success(new DcpRequestContext(context.holder(), claims));
    }

    private ServiceResult<ClaimToken> parseClaims(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            monitor.warning("X-Road claim propagation: malformed JWT (expected three dot-separated segments)");
            return ServiceResult.badRequest("Malformed JWT");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = OBJECT_MAPPER.readValue(new String(payload, StandardCharsets.UTF_8), CLAIMS_TYPE);
            return ServiceResult.success(ClaimToken.Builder.newInstance().claims(claims).build());
        } catch (IllegalArgumentException | java.io.IOException e) {
            monitor.warning("X-Road claim propagation: failed to decode JWT payload: " + e.getMessage());
            return ServiceResult.badRequest("Failed to decode JWT payload");
        }
    }
}
