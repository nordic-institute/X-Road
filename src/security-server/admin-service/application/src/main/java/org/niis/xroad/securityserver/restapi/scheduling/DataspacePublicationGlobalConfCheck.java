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
package org.niis.xroad.securityserver.restapi.scheduling;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.securityserver.restapi.service.HostedMembers;
import org.niis.xroad.securityserver.restapi.service.MemberPublicationCheck;
import org.niis.xroad.securityserver.restapi.service.PublicationCheckAction;
import org.niis.xroad.securityserver.restapi.service.SystemPublicationCheck;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.PublicationState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

/**
 * Step of the globalconf check that compares the SYSTEM values and the member DIDs on the binding rows with what the
 * reloaded globalconf carries. A submitted value that globalconf carries becomes published. A published SYSTEM value
 * that globalconf no longer carries while the authentication certificate is registered, and a published member DID
 * that globalconf no longer carries while the member is hosted on this server, go back to not sent, so the next
 * reconciler pass sends them again. The SYSTEM values and the member DIDs are checked independently.
 *
 * <p>Runs after the transaction of the globalconf check has committed, so the members hosted here are read from the
 * committed serverconf, including the client statuses of that check. A globalconf check that rolls back publishes
 * nothing. The globalconf check skips secondary nodes and backup restores. Never throws: a failure is logged and the
 * next check retries. Each step reads and transitions in its own transaction.
 */
@Slf4j
@Component
public class DataspacePublicationGlobalConfCheck {

    private static final Set<PublicationState> SENT = Set.of(PublicationState.SUBMITTED, PublicationState.PUBLISHED);

    private final DsParticipantRepository dsParticipantRepository;
    private final ClientRepository clientRepository;
    private final GlobalConfProvider globalConfProvider;
    private final DataspaceReadinessPredicates readinessPredicates;
    private final DataspaceDidAuthority didAuthority;
    private final TransactionTemplate transactionTemplate;

    public DataspacePublicationGlobalConfCheck(DsParticipantRepository dsParticipantRepository,
            ClientRepository clientRepository, GlobalConfProvider globalConfProvider,
            DataspaceReadinessPredicates readinessPredicates, DataspaceDidAuthority didAuthority,
            PlatformTransactionManager transactionManager) {
        this.dsParticipantRepository = dsParticipantRepository;
        this.clientRepository = clientRepository;
        this.globalConfProvider = globalConfProvider;
        this.readinessPredicates = readinessPredicates;
        this.didAuthority = didAuthority;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Runs one check against the already reloaded globalconf.
     *
     * @param securityServerId the identifier of this Security Server
     */
    public void check(SecurityServerId securityServerId) {
        runStep("SYSTEM values", () -> checkSystemValues(securityServerId));
        runStep("member DIDs", () -> checkMemberDids(securityServerId));
    }

    private void runStep(String subject, Runnable step) {
        try {
            transactionTemplate.executeWithoutResult(_ -> step.run());
        } catch (Exception e) {
            log.error("Dataspace publication: globalconf check of the {} failed, the next check retries", subject, e);
        }
    }

    private void checkSystemValues(SecurityServerId securityServerId) {
        dsParticipantRepository.findActiveSystemParticipant().ifPresent(system -> {
            var action = SystemPublicationCheck.decide(system.getPublicationState(), system.getDid(),
                    () -> didAuthority.dspBaseUrlIfValid().orElse(null),
                    globalConfProvider.getServerSystemValues(securityServerId).orElse(null),
                    readinessPredicates::hasRegisteredAuthCert);
            if (action != PublicationCheckAction.NONE) {
                transition(system, "SYSTEM values", action.expected(), action.next());
            }
        });
    }

    private void checkMemberDids(SecurityServerId securityServerId) {
        var hostedMembers = HostedMembers.of(clientRepository.getAllLocalClients());
        for (var row : dsParticipantRepository.findActiveMembers(SENT)) {
            var member = row.getMemberIdentifier();
            var action = MemberPublicationCheck.decide(row.getPublicationState(), row.getDid(),
                    globalConfProvider.getMemberDid(member, securityServerId).orElse(null),
                    hostedMembers.contains(member));
            if (action != PublicationCheckAction.NONE) {
                transition(row, "member DID of " + member.asEncodedId(), action.expected(), action.next());
            }
        }
    }

    private void transition(DsParticipantEntity row, String subject, PublicationState expected, PublicationState next) {
        if (dsParticipantRepository.transitionPublication(row.getId(), expected, next, null)) {
            log.info("Dataspace publication: {} moved from {} to {} by the globalconf check", subject, expected, next);
        } else {
            log.debug("Dataspace publication: {} changed concurrently, globalconf check result not applied", subject);
        }
    }
}
