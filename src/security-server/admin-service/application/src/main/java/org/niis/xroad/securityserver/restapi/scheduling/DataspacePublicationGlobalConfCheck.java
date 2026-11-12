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

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.securityserver.restapi.service.DifferenceEvaluator;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals;
import org.niis.xroad.securityserver.restapi.service.HostedMembers;
import org.niis.xroad.securityserver.restapi.service.PublicationCheck;
import org.niis.xroad.securityserver.restapi.service.PublicationCheckAction;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.model.PublicationState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Step of the globalconf check that compares the SYSTEM values and the member DIDs on the binding rows with what the
 * reloaded globalconf carries. It looks up the registered address of this Security Server once and runs three
 * steps, each in its own transaction:
 * <ol>
 *     <li>the SYSTEM values, decided by {@link PublicationCheck};</li>
 *     <li>the member DIDs of the rows in a sent state, decided by {@link PublicationCheck};</li>
 *     <li>the differences between the binding rows and globalconf, evaluated afresh from the rows as they are
 *     then and handed to {@link DataspaceDifferenceLog}.</li>
 * </ol>
 * {@link PublicationCheck} holds the transition rules.
 *
 * <p>Runs after the transaction of the globalconf check has committed, so the members hosted here are read from the
 * committed serverconf, including the client statuses of that check. A globalconf check that rolls back publishes
 * nothing. The globalconf check skips secondary nodes and backup restores. Never throws: a failure, of the address
 * lookup, of the signal setup or of a step, is logged and the next check retries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataspacePublicationGlobalConfCheck {

    private final DsParticipantRepository dsParticipantRepository;
    private final ClientRepository clientRepository;
    private final DifferenceEvaluator differenceEvaluator;
    private final DataspaceDifferenceLog differenceLog;
    private final DataspaceReadinessPredicates readinessPredicates;
    private final DataspaceDidAuthority didAuthority;
    private final OwnSecurityServerResolver ownSecurityServerResolver;
    private final AdminServiceProperties adminServiceProperties;
    private final PlatformTransactionManager transactionManager;

    @Setter(AccessLevel.PACKAGE)
    private Clock clock = Clock.systemUTC();

    /**
     * Runs one check against the already reloaded globalconf.
     *
     * @param securityServerId the identifier of this Security Server
     */
    public void check(SecurityServerId securityServerId) {
        OwnAddress address;
        DifferenceSignals signals;
        try {
            address = ownSecurityServerResolver.address(securityServerId);
            signals = differenceEvaluator.signals(securityServerId, address);
        } catch (Exception e) {
            logFailure("own address lookup and signal setup", e);
            return;
        }
        runStep("SYSTEM values", () -> checkSystemValues(signals));
        runStep("member DIDs", () -> checkMemberDids(signals));
        runStep("differences", () -> differenceLog.record(differenceEvaluator.evaluate(address)));
    }

    private void runStep(String subject, Runnable step) {
        var transactionTemplate = new TransactionTemplate(transactionManager,
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        try {
            transactionTemplate.executeWithoutResult(_ -> step.run());
        } catch (Exception e) {
            logFailure(subject, e);
        }
    }

    private static void logFailure(String subject, Exception e) {
        log.error("Dataspace publication: globalconf check of the {} failed, the next check retries", subject, e);
    }

    private void checkSystemValues(DifferenceSignals signals) {
        dsParticipantRepository.findActiveSystemParticipant().ifPresent(system -> {
            var action = PublicationCheck.decide(system.getPublicationState(),
                    () -> signals.compareSystemValues(system, didAuthority.dspBaseUrlIfValid().orElse(null)).overall(),
                    readinessPredicates::hasRegisteredAuthCert, system.getPublicationChangedAt(), submittedBefore());
            if (action != PublicationCheckAction.NONE) {
                transition(system, "SYSTEM values", action.expected(), action.next());
            }
        });
    }

    private void checkMemberDids(DifferenceSignals signals) {
        var rows = dsParticipantRepository.findActiveMembers(PublicationState.SENT);
        if (rows.isEmpty()) {
            return;
        }
        var hostedMembers = HostedMembers.of(clientRepository.getAllLocalClients());
        var submittedBefore = submittedBefore();
        for (var row : rows) {
            var member = row.getMemberIdentifier();
            var action = PublicationCheck.decide(row.getPublicationState(), () -> signals.compareMemberDid(row),
                    () -> hostedMembers.contains(member), row.getPublicationChangedAt(), submittedBefore);
            if (action != PublicationCheckAction.NONE) {
                transition(row, "member DID of " + member.asEncodedId(), action.expected(), action.next());
            }
        }
    }

    private Instant submittedBefore() {
        var minutes = adminServiceProperties.getDataspace().getPublicationSubmittedTimeoutMinutes();
        return clock.instant().minus(Duration.ofMinutes(minutes));
    }

    private void transition(DsParticipantEntity row, String subject, PublicationState expected, PublicationState next) {
        if (dsParticipantRepository.transitionPublication(row.getId(), expected, next, null)) {
            log.info("Dataspace publication: {} moved from {} to {} by the globalconf check", subject, expected, next);
        } else {
            log.debug("Dataspace publication: {} changed concurrently, globalconf check result not applied", subject);
        }
    }
}
