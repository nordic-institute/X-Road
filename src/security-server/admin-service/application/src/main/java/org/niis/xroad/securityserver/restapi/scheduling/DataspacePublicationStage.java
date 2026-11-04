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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.securityserver.restapi.service.DidRegistrationSender;
import org.niis.xroad.securityserver.restapi.service.PublicationOutcomeClassifier;
import org.niis.xroad.securityserver.restapi.service.SystemDidRegistrationSendOutcome;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.PublicationState;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Last stage of every provisioning reconciler pass: sends the SYSTEM values, the SYSTEM DID together with
 * the DSP base URL, to the Central Server once the server is ready and stores the classified result on the
 * SYSTEM binding row. Exists only where the reconciler is active, so a secondary node never sends.
 *
 * <p>A value is sent only when the data space is ready, the authentication certificate registration is
 * approved, the registered address is known and the SYSTEM binding is bound, provisioned and not sent.
 * Values already submitted, published or rejected are never sent again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(DataspaceParticipantProvisioningWorker.IsActive.class)
public class DataspacePublicationStage {

    private final DataspaceReadinessPredicates readinessPredicates;
    private final DataspaceDidAuthority didAuthority;
    private final DsParticipantRepository dsParticipantRepository;
    private final DidRegistrationSender didRegistrationSender;

    /**
     * Runs one publication step. Never throws: a failure is logged and the next pass retries.
     *
     * @param provisioned the contexts that were converged at the start of the pass or whose anchor the identity hub
     *                    confirmed while ensuring them in this pass
     */
    public void publish(Set<ParticipantContext> provisioned) {
        try {
            publishSystemValues(provisioned);
        } catch (Exception e) {
            log.error("Dataspace publication: SYSTEM values step failed, the next pass retries", e);
        }
    }

    private void publishSystemValues(Set<ParticipantContext> provisioned) {
        if (provisioned.stream().noneMatch(context -> context.kind() == ParticipantKind.SYSTEM)) {
            log.debug("Dataspace publication: SYSTEM context not provisioned, nothing to send");
            return;
        }
        var row = notSentSystemRow();
        if (row.isEmpty()) {
            return;
        }
        if (!readinessPredicates.isDataspaceReady()) {
            log.debug("Dataspace publication: dataspace not ready, not sending the SYSTEM values");
            return;
        }
        var authCert = readinessPredicates.registeredAuthCertBytes();
        if (authCert.isEmpty()) {
            log.debug("Dataspace publication: auth cert not registered, not sending the SYSTEM values");
            return;
        }
        var dspBaseUrl = didAuthority.dspBaseUrlIfValid();
        if (dspBaseUrl.isEmpty()) {
            log.debug("Dataspace publication: registered address unknown or invalid, not sending the SYSTEM values");
            return;
        }

        var outcome = send(row.get().getDid(), dspBaseUrl.get(), authCert.get());
        apply(row.get(), PublicationOutcomeClassifier.classify(outcome));
    }

    private Optional<DsParticipantEntity> notSentSystemRow() {
        return dsParticipantRepository.findSystemParticipant()
                .filter(row -> row.getState() == ParticipantState.ACTIVE)
                .filter(row -> row.getPublicationState() == null || row.getPublicationState() == PublicationState.NOT_SENT);
    }

    private SystemDidRegistrationSendOutcome send(String systemDid, String dspBaseUrl, byte[] authCert) {
        try {
            return didRegistrationSender.sendSystemDidRegisterRequest(systemDid, dspBaseUrl, authCert);
        } catch (Exception e) {
            logSendFailure(e);
            return SystemDidRegistrationSendOutcome.sendFailure(e);
        }
    }

    private static void logSendFailure(Exception e) {
        if (e instanceof XrdRuntimeException xrd && xrd.isCausedBy(ErrorCode.GLOBAL_CONF_OUTDATED)) {
            log.debug("Dataspace publication: sending the SYSTEM values failed, global configuration is outdated: {}",
                    xrd.getMessage());
        } else {
            log.warn("Dataspace publication: sending the SYSTEM values failed", e);
        }
    }

    private void apply(DsParticipantEntity row, PublicationOutcomeClassifier.Transition transition) {
        if (transition.changesNothing(row.getPublicationState(), row.getPublicationReason())) {
            log.debug("Dataspace publication: SYSTEM values still not sent ({}), reason unchanged", transition.backoff());
            return;
        }
        boolean applied = dsParticipantRepository.transitionPublication(row.getId(), PublicationState.NOT_SENT,
                transition.state(), transition.reason());
        if (applied) {
            log.info("Dataspace publication: SYSTEM values are now {}{}{}", transition.state(),
                    transition.reason() == null ? "" : " (" + transition.reason() + ")",
                    transition.state() == PublicationState.NOT_SENT ? ", backoff " + transition.backoff() : "");
        } else {
            log.debug("Dataspace publication: SYSTEM values changed concurrently, result not applied");
        }
    }
}
