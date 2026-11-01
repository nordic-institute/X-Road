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
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
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
    static final String CLAIM_SERVICE_VERSION = "service_version";
    static final String CLAIM_SCOPE = "scope";
    static final String CLAIM_GROUP = "group";

    private static final String SCOPE_METHOD_KEY = "method";
    private static final String SCOPE_PATH_KEY = "path";

    private static final String GROUP_TYPE_KEY = "type";
    private static final String GROUP_TYPE_LOCAL = "LOCAL";
    private static final String GROUP_TYPE_GLOBAL = "GLOBAL";
    private static final String GROUP_OWNER_KEY = "owner";
    private static final String GROUP_INSTANCE_KEY = "instance";
    private static final String GROUP_CODE_KEY = "code";

    private static final int MEMBER_CLIENT_ID_PARTS = 3;
    private static final int SUBSYSTEM_CLIENT_ID_PARTS = 4;

    private AgreementTokenClaimsCodec() {
    }

    static String encodeClient(ClientId client) {
        return client.asEncodedId();
    }

    static ClientId decodeClient(String encoded) {
        var parts = splitEncodedId(encoded, CLAIM_CLIENT);
        if (parts.length != MEMBER_CLIENT_ID_PARTS && parts.length != SUBSYSTEM_CLIENT_ID_PARTS) {
            throw new IllegalArgumentException("invalid client_id claim: " + encoded);
        }
        var subsystemCode = parts.length == SUBSYSTEM_CLIENT_ID_PARTS ? parts[MEMBER_CLIENT_ID_PARTS] : null;
        return ClientId.Conf.create(parts[0], parts[1], parts[2], subsystemCode);
    }

    static String encodeService(ServiceId service) {
        return service.getClientId().asEncodedId() + XRoadId.ENCODED_ID_SEPARATOR + service.getServiceCode();
    }

    static String encodeServiceVersion(ServiceId service) {
        return service.getServiceVersion();
    }

    static ServiceId decodeService(String encoded, String serviceVersion) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("service_id claim must not be blank");
        }
        var lastSeparator = encoded.lastIndexOf(XRoadId.ENCODED_ID_SEPARATOR);
        if (lastSeparator <= 0 || lastSeparator == encoded.length() - 1) {
            throw new IllegalArgumentException("invalid service_id claim: " + encoded);
        }
        var client = decodeClient(encoded.substring(0, lastSeparator));
        return ServiceId.Conf.create(client, encoded.substring(lastSeparator + 1), serviceVersion);
    }

    static String optionalStringClaim(JWTClaimsSet claimsSet, String name) {
        var value = claimsSet.getClaim(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String str) || str.isBlank()) {
            throw new IllegalArgumentException("blank or non-string claim '" + name + "'");
        }
        return str;
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

    static Map<String, Object> encodeGroup(AgreementTokenGroup group) {
        return switch (group) {
            case AgreementTokenGroup.Local local -> Map.of(
                    GROUP_TYPE_KEY, GROUP_TYPE_LOCAL,
                    GROUP_OWNER_KEY, encodeClient(local.owner()),
                    GROUP_CODE_KEY, local.groupId().getGroupCode());
            case AgreementTokenGroup.Global global -> Map.of(
                    GROUP_TYPE_KEY, GROUP_TYPE_GLOBAL,
                    GROUP_INSTANCE_KEY, global.groupId().getXRoadInstance(),
                    GROUP_CODE_KEY, global.groupId().getGroupCode());
        };
    }

    static AgreementTokenGroup decodeGroup(JWTClaimsSet claimsSet) {
        Map<String, Object> raw;
        try {
            raw = claimsSet.getJSONObjectClaim(CLAIM_GROUP);
        } catch (ParseException e) {
            throw new IllegalArgumentException("invalid group claim", e);
        }
        if (raw == null) {
            throw new IllegalArgumentException("group claim must not be empty");
        }
        var code = requireString(raw, GROUP_CODE_KEY, "group");
        return switch (requireString(raw, GROUP_TYPE_KEY, "group")) {
            case GROUP_TYPE_LOCAL -> new AgreementTokenGroup.Local(
                    decodeClient(requireString(raw, GROUP_OWNER_KEY, "group")), LocalGroupId.Conf.create(code));
            case GROUP_TYPE_GLOBAL -> new AgreementTokenGroup.Global(
                    GlobalGroupId.Conf.create(requireString(raw, GROUP_INSTANCE_KEY, "group"), code));
            default -> throw new IllegalArgumentException("unknown group type in group claim");
        };
    }

    static String requireStringClaim(JWTClaimsSet claimsSet, String name) {
        var value = claimsSet.getClaim(name);
        if (!(value instanceof String str) || str.isBlank()) {
            throw new IllegalArgumentException("missing or blank claim '" + name + "'");
        }
        return str;
    }

    private static String requireString(Map<?, ?> map, String key) {
        return requireString(map, key, "scope entry");
    }

    private static String requireString(Map<?, ?> map, String key, String what) {
        var value = map.get(key);
        if (!(value instanceof String str) || str.isBlank()) {
            throw new IllegalArgumentException(what + " missing '" + key + "'");
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
