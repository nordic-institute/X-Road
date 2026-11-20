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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage;
import org.niis.xroad.securityserver.restapi.repository.PendingAddressChangeRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceReIdentificationService;
import org.niis.xroad.securityserver.restapi.service.PendingAddressChangeAction;
import org.niis.xroad.securityserver.restapi.service.PendingAddressChangeCheck;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;

/**
 * Step of the globalconf check that acts on the pending address change. When the reloaded globalconf shows the
 * requested address it re-identifies the participants of this Security Server and drops the request; when the
 * request has waited longer than the configured expiry it drops the request. {@link PendingAddressChangeCheck}
 * decides.
 *
 * <p>Runs after the transaction of the globalconf check has ended, which skips secondary nodes and backup
 * restores. The re-identification runs in its own transaction and the request is dropped in another, whatever the
 * re-identification outcome. A refused re-identification, for example while the authentication certificate is not
 * registered, is not retried: the operator re-identify action stays available. Never throws: an unexpected failure
 * of the re-identification is logged and keeps the request, and the next check retries until the request expires.
 */
@Slf4j
@Component
public class PendingAddressChangeGlobalConfCheck {

    private final PendingAddressChangeRepository pendingAddressChangeRepository;
    private final DataspaceReIdentificationService reIdentificationService;
    private final OwnSecurityServerResolver ownSecurityServerResolver;
    private final AdminServiceProperties adminServiceProperties;
    private final TransactionTemplate newTransaction;

    @Setter(AccessLevel.PACKAGE)
    private Clock clock = Clock.systemUTC();

    public PendingAddressChangeGlobalConfCheck(PendingAddressChangeRepository pendingAddressChangeRepository,
            DataspaceReIdentificationService reIdentificationService,
            OwnSecurityServerResolver ownSecurityServerResolver, AdminServiceProperties adminServiceProperties,
            PlatformTransactionManager transactionManager) {
        this.pendingAddressChangeRepository = pendingAddressChangeRepository;
        this.reIdentificationService = reIdentificationService;
        this.ownSecurityServerResolver = ownSecurityServerResolver;
        this.adminServiceProperties = adminServiceProperties;
        this.newTransaction = new TransactionTemplate(transactionManager,
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    /**
     * Runs one check against the already reloaded globalconf.
     *
     * @param securityServerId the identifier of this Security Server
     */
    public void check(SecurityServerId securityServerId) {
        try {
            var action = newTransaction.execute(_ -> decide(securityServerId));
            if (action == PendingAddressChangeAction.FLIP) {
                reIdentifyAndDropRequest();
            }
        } catch (Exception e) {
            log.error("Address change: globalconf check failed, the next check retries", e);
        }
    }

    private PendingAddressChangeAction decide(SecurityServerId securityServerId) {
        return pendingAddressChangeRepository.find().map(pending -> {
            var registeredAddress = ownSecurityServerResolver.address(securityServerId).registeredAddress().orElse(null);
            var action = PendingAddressChangeCheck.decide(pending, registeredAddress, clock.instant(), expiry());
            if (action == PendingAddressChangeAction.EXPIRE) {
                pendingAddressChangeRepository.delete();
                log.info("Address change: globalconf did not show the requested address in time, request dropped");
            } else if (action == PendingAddressChangeAction.KEEP) {
                log.debug("Address change: globalconf does not show the requested address yet");
            }
            return action;
        }).orElse(PendingAddressChangeAction.KEEP);
    }

    private void reIdentifyAndDropRequest() {
        try {
            int retired = newTransaction.execute(_ -> reIdentificationService.reIdentify());
            log.info("Address change: globalconf shows the requested address, {} binding(s) re-identified", retired);
        } catch (ConflictException e) {
            logRefusal(e);
        } catch (Exception e) {
            log.error("Address change: re-identification failed, the request stays until it expires and the next "
                    + "check retries", e);
            dropRequestIfExpired();
            return;
        }
        newTransaction.executeWithoutResult(_ -> pendingAddressChangeRepository.delete());
    }

    private static void logRefusal(ConflictException e) {
        if (ErrorMessage.RE_IDENTIFICATION_PROVISIONING_INACTIVE.build().code().equals(e.getErrorDeviation().code())) {
            log.debug("Address change: the participants were not re-identified, dataspace provisioning is inactive");
        } else {
            log.info("Address change: the participants were not re-identified, use the re-identify action: {}",
                    e.getMessage());
        }
    }

    private void dropRequestIfExpired() {
        newTransaction.executeWithoutResult(_ -> pendingAddressChangeRepository.find()
                .filter(pending -> PendingAddressChangeCheck.isExpired(pending, clock.instant(), expiry()))
                .ifPresent(_ -> {
                    pendingAddressChangeRepository.delete();
                    log.warn("Address change: re-identification kept failing until the request expired, request dropped");
                }));
    }

    private Duration expiry() {
        return adminServiceProperties.getAddressChangeExpiry();
    }
}
