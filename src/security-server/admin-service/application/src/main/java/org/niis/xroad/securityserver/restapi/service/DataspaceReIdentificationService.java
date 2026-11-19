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
package org.niis.xroad.securityserver.restapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.ds.identity.RegisteredHostMatcher;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantProvisioningTrigger;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.RE_IDENTIFICATION_ADDRESS_UNKNOWN;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.RE_IDENTIFICATION_CANNOT_BIND;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.RE_IDENTIFICATION_NOT_NEEDED;

/**
 * Re-identifies the participants of the Security Server after its registered address changed: retires every active
 * binding whose DID host differs from the registered address. The provisioning worker then tears the retired
 * contexts down and binds new DIDs at the registered address. Sends no management request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataspaceReIdentificationService {

    private final OwnSecurityServerResolver ownSecurityServerResolver;
    private final DataspaceReadinessPredicates readinessPredicates;
    private final DsParticipantRepository dsParticipantRepository;
    private final DataspaceParticipantProvisioningTrigger provisioningTrigger;

    /**
     * Re-identifies on the operator's request. Retires nothing unless the registered address is known and the
     * provisioning worker could bind again. Starts the provisioning worker after the commit when it retired a
     * binding.
     *
     * @return the number of bindings retired, always above 0
     * @throws ConflictException when the registered address is not known, when the worker could not bind again, or
     *                           when no active binding differs from the registered address
     */
    @Transactional
    public int reIdentifyOnOperatorRequest() {
        var matcher = ownSecurityServerResolver.address().registeredAddress()
                .map(RegisteredHostMatcher::of)
                .filter(RegisteredHostMatcher::isRegisteredAddressUsable)
                .orElseThrow(() -> new ConflictException(RE_IDENTIFICATION_ADDRESS_UNKNOWN.build()));
        if (!DataspaceReadinessPredicates.mayBind(
                readinessPredicates.isDataspaceReady(), readinessPredicates.hasRegisteredAuthCert())) {
            throw new ConflictException(RE_IDENTIFICATION_CANNOT_BIND.build());
        }
        int retired = dsParticipantRepository.retireDifferingBindings(matcher);
        if (retired == 0) {
            throw new ConflictException(RE_IDENTIFICATION_NOT_NEEDED.build());
        }
        log.info("Dataspace: re-identification retired {} binding(s) whose DID host differs from the registered address",
                retired);
        provisioningTrigger.provisionParticipantAsync();
        return retired;
    }
}
