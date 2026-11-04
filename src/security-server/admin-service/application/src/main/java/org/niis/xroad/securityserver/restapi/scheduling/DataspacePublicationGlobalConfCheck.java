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
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.securityserver.restapi.service.SystemPublicationCheck;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.PublicationState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step of the globalconf check that compares the SYSTEM values on the SYSTEM binding row with what the reloaded
 * globalconf carries. A submitted value that globalconf carries becomes published. A published value that globalconf
 * no longer carries while the authentication certificate is registered goes back to not sent, so the next reconciler
 * pass sends it again.
 *
 * <p>Runs inside the globalconf check, which skips secondary nodes and backup restores. Never throws: a failure is
 * logged and the next check retries. The repository reads and the transition run in their own transaction, so a
 * failure does not mark the transaction of the globalconf check rollback-only.
 */
@Slf4j
@Component
public class DataspacePublicationGlobalConfCheck {

    private final DsParticipantRepository dsParticipantRepository;
    private final GlobalConfProvider globalConfProvider;
    private final DataspaceReadinessPredicates readinessPredicates;
    private final DataspaceDidAuthority didAuthority;
    private final TransactionTemplate transactionTemplate;

    public DataspacePublicationGlobalConfCheck(DsParticipantRepository dsParticipantRepository,
            GlobalConfProvider globalConfProvider, DataspaceReadinessPredicates readinessPredicates,
            DataspaceDidAuthority didAuthority, PlatformTransactionManager transactionManager) {
        this.dsParticipantRepository = dsParticipantRepository;
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
        try {
            transactionTemplate.executeWithoutResult(_ -> checkSystemValues(securityServerId));
        } catch (Exception e) {
            log.error("Dataspace publication: globalconf check of the SYSTEM values failed, the next check retries", e);
        }
    }

    private void checkSystemValues(SecurityServerId securityServerId) {
        dsParticipantRepository.findActiveSystemParticipant().ifPresent(system -> {
            var action = SystemPublicationCheck.decide(system.getPublicationState(), system.getDid(),
                    () -> didAuthority.dspBaseUrlIfValid().orElse(null),
                    globalConfProvider.getServerSystemValues(securityServerId).orElse(null),
                    readinessPredicates::hasRegisteredAuthCert);
            if (action != SystemPublicationCheck.Action.NONE) {
                transition(system, action.expected(), action.next());
            }
        });
    }

    private void transition(DsParticipantEntity row, PublicationState expected, PublicationState next) {
        if (dsParticipantRepository.transitionPublication(row.getId(), expected, next, null)) {
            log.info("Dataspace publication: SYSTEM values moved from {} to {} by the globalconf check", expected, next);
        } else {
            log.debug("Dataspace publication: SYSTEM values changed concurrently, globalconf check result not applied");
        }
    }
}
