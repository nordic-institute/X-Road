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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.agreementtoken.AgreementTokenGrant;
import org.niis.xroad.common.agreementtoken.AgreementTokenMinter;
import org.niis.xroad.common.agreementtoken.AgreementTokenProtocolProperties;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.proxy.controlplane.AgreementGrant;
import org.niis.xroad.proxy.controlplane.AgreementGrantRpcClient;
import org.niis.xroad.proxy.core.configuration.AgreementTokenKeyMaterial;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.List;
import java.util.Optional;

/**
 * Resolves, filters and mints the agreement token for a data-flow prepare/start message: looks up the grant
 * behind the agreement, keeps only the scope entries the client's live ACL still grants for the service, and
 * mints a token over what remains. Never throws — every failure mode (no grant, an ACL entry withdrawn since
 * negotiation, key material unavailable, or anything else going wrong) yields an empty result, so the caller
 * always has a safe endpoint-only fallback.
 */
@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class AgreementTokenIssuer {

    private final AgreementGrantRpcClient grantRpcClient;
    private final ServerConfProvider serverConfProvider;
    private final AgreementTokenKeyMaterial keyMaterial;
    private final AgreementTokenProtocolProperties protocolProperties;

    /**
     * @param agreementId the negotiated agreement's id, as carried by the prepare/start message
     * @return the minted, compact-serialized token, or empty when nothing may be minted
     */
    public Optional<String> issueToken(String agreementId) {
        if (agreementId == null || agreementId.isBlank()) {
            return Optional.empty();
        }
        try {
            return issueTokenInternal(agreementId);
        } catch (Exception e) {
            log.warn("Could not mint an agreement token for agreement id '{}'; falling back to endpoint-only", agreementId, e);
            return Optional.empty();
        }
    }

    private Optional<String> issueTokenInternal(String agreementId) {
        var keyProviderOpt = keyMaterial.provider();
        if (keyProviderOpt.isEmpty()) {
            log.debug("Agreement-token signing key material unavailable; not minting for agreement id '{}'", agreementId);
            return Optional.empty();
        }

        var grantOpt = grantRpcClient.resolveAgreementGrant(agreementId);
        if (grantOpt.isEmpty()) {
            return Optional.empty();
        }
        var grant = grantOpt.get();

        var liveScope = filterToLiveAcl(grant);
        if (liveScope.isEmpty()) {
            log.info("No live ACL entry backs any granted scope for agreement id '{}'; not minting", agreementId);
            return Optional.empty();
        }

        var minter = new AgreementTokenMinter(keyProviderOpt.get(), protocolProperties);
        var token = minter.mint(new AgreementTokenGrant(agreementId, grant.client(), grant.service(), liveScope));
        return Optional.of(token);
    }

    /**
     * Drops every granted scope entry that is not an exact match — method compared case-insensitively, path
     * compared exactly — of one of the client's current ACL endpoint entries for the service, so a token can
     * never restate a broader grant than the live ACL backs.
     */
    private List<AgreementTokenScope> filterToLiveAcl(AgreementGrant grant) {
        var aclEndpoints = serverConfProvider.getAclEndpoints(grant.client(), grant.service());
        return grant.scope().stream()
                .filter(scope -> aclEndpoints.stream()
                        .anyMatch(endpoint -> endpoint.getMethod().equalsIgnoreCase(scope.method())
                                && endpoint.getPath().equals(scope.path())))
                .toList();
    }
}
