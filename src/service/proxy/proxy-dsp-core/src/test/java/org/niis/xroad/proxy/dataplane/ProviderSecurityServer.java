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

package org.niis.xroad.proxy.dataplane;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.niis.xroad.common.agreementtoken.AgreementTokenGroupAccess;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.proxy.controlplane.AgreementGrant;
import org.niis.xroad.proxy.controlplane.AgreementGrantRpcClient;
import org.niis.xroad.proxy.core.configuration.AgreementTokenKeyMaterial;
import org.niis.xroad.proxy.core.configuration.ProxyAgreementTokenProperties;
import org.niis.xroad.proxy.core.serverproxy.AgreementTokenAccessCheck;
import org.niis.xroad.proxy.core.service.LegacyCallRefusalPolicy;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.Endpoint;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One provider Security Server, built from its ACL and its agreement token key set. The real
 * {@link AgreementTokenIssuer} mints for it and the real {@link AgreementTokenAccessCheck} judges for it; only
 * their collaborators (grant lookup, server configuration, key material, token properties, legacy call refusal
 * policy) are stubbed. Two instances with their own key sets stand for two sibling Security Servers of one member.
 */
final class ProviderSecurityServer {

    private static final String AGREEMENT_ID = "agreement-1";
    private static final String ISSUER = "x-road-provider-data-plane";
    private static final String AUDIENCE = "x-road-server-proxy";
    private static final Duration TOKEN_TTL = Duration.ofSeconds(60);

    private final AgreementGrantRpcClient grantRpcClient = mock(AgreementGrantRpcClient.class);
    private final AgreementTokenIssuer issuer;
    private final AgreementTokenAccessCheck accessCheck;

    private ProviderSecurityServer(Acl acl, TestAgreementTokenKeyProvider keys) {
        var serverConfProvider = mock(ServerConfProvider.class);
        acl.endpointsByCaller.forEach((caller, endpoints) ->
                when(serverConfProvider.getAclEndpoints(caller.client(), caller.service())).thenReturn(endpoints));

        var keyMaterial = mock(AgreementTokenKeyMaterial.class);
        when(keyMaterial.provider()).thenReturn(Optional.of(keys));

        var properties = mock(ProxyAgreementTokenProperties.class);
        when(properties.issuer()).thenReturn(ISSUER);
        when(properties.audience()).thenReturn(AUDIENCE);
        when(properties.tokenTtl()).thenReturn(TOKEN_TTL);
        when(properties.expiryLeeway()).thenReturn(Duration.ZERO);

        var legacyCallRefusalPolicy = mock(LegacyCallRefusalPolicy.class);
        when(legacyCallRefusalPolicy.refusedAsProvider()).thenReturn(false);

        issuer = new AgreementTokenIssuer(grantRpcClient, serverConfProvider, keyMaterial, properties);
        accessCheck = new AgreementTokenAccessCheck(keyMaterial, properties, legacyCallRefusalPolicy,
                AgreementTokenGroupAccess.NONE);
    }

    static ProviderSecurityServer create(Acl acl, TestAgreementTokenKeyProvider keys) {
        return new ProviderSecurityServer(acl, keys);
    }

    /**
     * Mints a token for an agreement negotiated for the given client, service, method and path. The scope of the
     * token comes from this server's live ACL, not from the negotiated method and path.
     *
     * @return the token, or empty when this server's ACL grants the client nothing for the service
     */
    Optional<String> issueToken(ClientId client, ServiceId service, String method, String path) {
        when(grantRpcClient.resolveAgreementGrant(AGREEMENT_ID))
                .thenReturn(Optional.of(new AgreementGrant(client, service, List.of(new AgreementTokenScope(method, path)))));
        return issuer.issueToken(AGREEMENT_ID);
    }

    /**
     * @return this server's decision for a REST request carrying the given token
     */
    AgreementTokenAccessCheck.Decision checkToken(String token, ClientId client, ServiceId service, String method, String path) {
        return accessCheck.decide(token, client, service, method, path);
    }

    /**
     * The endpoints a provider Security Server's ACL grants to each client for each service.
     */
    static final class Acl {

        private final Map<Caller, List<Endpoint>> endpointsByCaller;

        private Acl(Map<Caller, List<Endpoint>> endpointsByCaller) {
            this.endpointsByCaller = endpointsByCaller;
        }

        static Acl empty() {
            return new Acl(Map.of());
        }

        Acl allowing(ClientId client, ServiceId service, Endpoint... endpoints) {
            var extended = new HashMap<>(endpointsByCaller);
            extended.put(new Caller(client, service), List.of(endpoints));
            return new Acl(extended);
        }
    }

    private record Caller(ClientId client, ServiceId service) {
    }
}
