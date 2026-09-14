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
package org.niis.xroad.cs.admin.core.dataspace;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.Set;

import static org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID;

/**
 * Reacts to {@link ServerClientRemovedEvent}s once the removing transaction has committed: if the
 * member (counting the member and all its subsystems) now has no registered client left on that
 * security server, and is not the server's owner, its membership credential for that server is
 * revoked at the collocated issuer. Runs after commit so an issuer outage never slows management
 * request processing and a rolled-back removal never triggers a revocation; failures are logged and
 * swallowed, leaving the credential's natural expiry as the backstop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberCredentialRevocationService {

    private final SecurityServerRepository securityServers;
    private final XRoadMemberRepository members;
    private final ServerClientRepository serverClients;
    private final IssuerProvisioningRpcClient rpcClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onServerClientRemoved(ServerClientRemovedEvent event) {
        try {
            revokeIfLastClientRemoved(event);
        } catch (Exception e) {
            log.warn("Failed to revoke dataspace credential for member {} on security server {}: {}",
                    event.memberId(), event.securityServerId(), e.getMessage(), e);
        }
    }

    private void revokeIfLastClientRemoved(ServerClientRemovedEvent event) {
        SecurityServerEntity server = securityServers.findBy(event.securityServerId()).orElse(null);
        if (server == null) {
            return;
        }
        if (ClientId.equals(server.getOwner().getIdentifier(), event.memberId())) {
            return;
        }
        XRoadMemberEntity member = members.findMember(event.memberId()).orElse(null);
        if (member == null || hasRemainingClientRows(server, member)) {
            return;
        }

        String holderDid = ParticipantIdentifierScheme.memberDid(event.memberId(), server.getAddress());
        int revokedCount = rpcClient.revokeCredential(ISSUER_PARTICIPANT_ID, holderDid, event.removedAt());

        log.info("Revoked {} dataspace credential(s) for member {} on security server {}",
                revokedCount, event.memberId(), event.securityServerId());
    }

    private boolean hasRemainingClientRows(SecurityServerEntity server, XRoadMemberEntity member) {
        Set<SecurityServerClientEntity> memberAndSubsystems = new HashSet<>(member.getSubsystems());
        memberAndSubsystems.add(member);
        return serverClients.countBySecurityServerAndSecurityServerClientIn(server, memberAndSubsystems) > 0;
    }
}
