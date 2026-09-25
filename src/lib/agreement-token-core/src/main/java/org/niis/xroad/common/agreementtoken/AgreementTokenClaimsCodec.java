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
package org.niis.xroad.common.agreementtoken;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import com.nimbusds.jwt.JWTClaimsSet;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts between the typed grant/claims model and the plain-string/JSON shape a JWT claims set carries on
 * the wire. Shared by {@link AgreementTokenMinter} (encode) and {@link AgreementTokenVerifier} (decode) so the
 * two directions can never drift apart. Every decode method throws a plain {@link IllegalArgumentException} on
 * malformed input — the token is untrusted, adversarial data at this point, and the verifier turns that
 * exception into a {@link AgreementTokenRejectionReason#MALFORMED_TOKEN} result rather than letting it escape.
 */
final class AgreementTokenClaimsCodec {

    static final String CLAIM_AGREEMENT_ID = "agreement_id";
    static final String CLAIM_CLIENT = "client_id";
    static final String CLAIM_SERVICE = "service_id";
    static final String CLAIM_SCOPE = "scope";

    private static final String SCOPE_METHOD_KEY = "method";
    private static final String SCOPE_PATH_KEY = "path";

    private static final int MEMBER_CLIENT_ID_PARTS = 3;
    private static final int SUBSYSTEM_CLIENT_ID_PARTS = 4;

    private AgreementTokenClaimsCodec() {
    }

    static String encodeClient(ClientId client) {
        return client.asEncodedId();
    }

    static ClientId decodeClient(String encoded) {
        var parts = splitEncodedId(encoded, "client_id");
        if (parts.length != MEMBER_CLIENT_ID_PARTS && parts.length != SUBSYSTEM_CLIENT_ID_PARTS) {
            throw new IllegalArgumentException("invalid client_id claim: " + encoded);
        }
        var subsystemCode = parts.length == SUBSYSTEM_CLIENT_ID_PARTS ? parts[MEMBER_CLIENT_ID_PARTS] : null;
        return ClientId.Conf.create(parts[0], parts[1], parts[2], subsystemCode);
    }

    static String encodeService(ServiceId service) {
        var clientPart = service.getClientId().asEncodedId();
        var fullServiceCode = service.getServiceVersion() == null
                ? service.getServiceCode()
                : service.getServiceCode() + "." + service.getServiceVersion();
        return clientPart + XRoadId.ENCODED_ID_SEPARATOR + fullServiceCode;
    }

    static ServiceId decodeService(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("service_id claim must not be blank");
        }
        var lastSeparator = encoded.lastIndexOf(XRoadId.ENCODED_ID_SEPARATOR);
        if (lastSeparator <= 0 || lastSeparator == encoded.length() - 1) {
            throw new IllegalArgumentException("invalid service_id claim: " + encoded);
        }
        var client = decodeClient(encoded.substring(0, lastSeparator));
        var fullServiceCode = encoded.substring(lastSeparator + 1);
        var dotIndex = fullServiceCode.indexOf('.');
        if (dotIndex < 0) {
            return ServiceId.Conf.create(client, fullServiceCode);
        }
        return ServiceId.Conf.create(client, fullServiceCode.substring(0, dotIndex), fullServiceCode.substring(dotIndex + 1));
    }

    static List<Map<String, Object>> encodeScope(List<AgreementTokenScope> scope) {
        return scope.stream()
                .map(entry -> Map.<String, Object>of(SCOPE_METHOD_KEY, entry.method(), SCOPE_PATH_KEY, entry.path()))
                .toList();
    }

    static List<AgreementTokenScope> decodeScope(JWTClaimsSet claimsSet) {
        List<?> raw;
        try {
            raw = claimsSet.getListClaim(CLAIM_SCOPE);
        } catch (ParseException e) {
            throw new IllegalArgumentException("invalid scope claim", e);
        }
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("scope claim must not be empty");
        }
        var scope = new ArrayList<AgreementTokenScope>(raw.size());
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("scope entry is not an object");
            }
            scope.add(new AgreementTokenScope(requireString(map, SCOPE_METHOD_KEY), requireString(map, SCOPE_PATH_KEY)));
        }
        return List.copyOf(scope);
    }

    static String requireStringClaim(JWTClaimsSet claimsSet, String name) {
        var value = claimsSet.getClaim(name);
        if (!(value instanceof String str) || str.isBlank()) {
            throw new IllegalArgumentException("missing or blank claim '" + name + "'");
        }
        return str;
    }

    private static String requireString(Map<?, ?> map, String key) {
        var value = map.get(key);
        if (!(value instanceof String str) || str.isBlank()) {
            throw new IllegalArgumentException("scope entry missing '" + key + "'");
        }
        return str;
    }

    private static String[] splitEncodedId(String encoded, String claimName) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException(claimName + " claim must not be blank");
        }
        return encoded.split(String.valueOf(XRoadId.ENCODED_ID_SEPARATOR));
    }
}
