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

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.ds.identity.RegisteredHostMatcher;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantProvisioningTrigger;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.impl.ownserver.OwnerNotInitialised;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.RE_IDENTIFICATION_ADDRESS_UNKNOWN;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.RE_IDENTIFICATION_CANNOT_BIND;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.RE_IDENTIFICATION_CANNOT_MINT;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.RE_IDENTIFICATION_NOT_NEEDED;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.RE_IDENTIFICATION_PROVISIONING_INACTIVE;

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
    private final DataspaceDidAuthority didAuthority;
    private final DsParticipantRepository dsParticipantRepository;
    private final DataspaceParticipantProvisioningTrigger provisioningTrigger;

    /**
     * Re-identifies on the operator's request: as {@link #reIdentify()}, and refuses when no binding differs from
     * the registered address.
     *
     * @return the number of bindings retired, always above 0
     * @throws ConflictException when {@link #reIdentify()} refuses, or when no active binding differs from the
     *                           registered address
     */
    @Transactional
    public int reIdentifyOnOperatorRequest() {
        int retired = reIdentify();
        if (retired == 0) {
            throw new ConflictException(RE_IDENTIFICATION_NOT_NEEDED.build());
        }
        return retired;
    }

    /**
     * Retires nothing unless the provisioning worker is active and the registered address is known. Returns 0 when no
     * active binding differs from the registered address. Otherwise it retires nothing unless the worker could bind
     * again and every DID it would bind can be created at the registered address. Retires exactly the bindings it
     * checked. Starts the provisioning worker after the commit when it retired a binding.
     *
     * @return the number of bindings retired, 0 when no active binding differs from the registered address
     * @throws ConflictException when the provisioning worker is not active, when the registered address is not
     *                           known, when the worker could not bind again, or when a DID cannot be created at
     *                           the registered address
     */
    @Transactional
    public int reIdentify() {
        if (!provisioningTrigger.isActive()) {
            throw new ConflictException(RE_IDENTIFICATION_PROVISIONING_INACTIVE.build());
        }
        var matcher = usableRegisteredAddressMatcher();
        var rowsToRetire = dsParticipantRepository.findDifferingBindings(matcher);
        if (rowsToRetire.isEmpty()) {
            return 0;
        }
        if (!DataspaceReadinessPredicates.mayBind(
                readinessPredicates.isDataspaceReady(), readinessPredicates.hasRegisteredAuthCert())) {
            throw new ConflictException(RE_IDENTIFICATION_CANNOT_BIND.build());
        }
        failUnlessEveryDidCanBeMinted(rowsToRetire);
        int retired = dsParticipantRepository.retireBindings(rowsToRetire.stream().map(DsParticipantEntity::getId).toList());
        if (retired > 0) {
            log.info("Dataspace: re-identification retired {} binding(s) whose DID host differs from the registered address",
                    retired);
            provisioningTrigger.provisionParticipantAsync();
        }
        return retired;
    }

    private RegisteredHostMatcher usableRegisteredAddressMatcher() {
        var matcher = switch (ownSecurityServerResolver.address()) {
            case OwnAddress.Registered(_, var address) -> RegisteredHostMatcher.of(address);
            case OwnAddress.NotRegistered _ -> throw addressUnknown("the server is not registered", null);
            case OwnAddress.GlobalConfUnavailable(var cause) -> throw addressUnknown("globalconf is unavailable: " + cause, cause);
            case OwnerNotInitialised _ -> throw addressUnknown("the server owner is not initialised", null);
        };
        if (!matcher.isRegisteredAddressUsable()) {
            throw addressUnknown("the registered address is not a usable host", null);
        }
        return matcher;
    }

    private static ConflictException addressUnknown(String reason, Throwable cause) {
        log.info("Dataspace: re-identification refused, the registered address is not known: {}", reason);
        return cause == null
                ? new ConflictException(RE_IDENTIFICATION_ADDRESS_UNKNOWN.build())
                : new ConflictException(cause, RE_IDENTIFICATION_ADDRESS_UNKNOWN.build());
    }

    private void failUnlessEveryDidCanBeMinted(List<DsParticipantEntity> rowsToRetire) {
        for (var row : rowsToRetire) {
            var result = mint(row);
            if (result instanceof DidMintResult.Rejected(var reason, var detail, var _)) {
                log.warn("Dataspace: re-identification refused, the DID of participant {} cannot be created: {} ({})",
                        row.getCtxId(), reason, detail);
                throw new ConflictException(RE_IDENTIFICATION_CANNOT_MINT.build());
            }
        }
    }

    private DidMintResult mint(DsParticipantEntity row) {
        try {
            return row.getParticipantType() == ParticipantType.SYSTEM
                    ? didAuthority.mintSystemDid()
                    : didAuthority.mintMemberDid(ClientId.Conf.ensure(row.getMemberIdentifier()));
        } catch (XrdRuntimeException e) {
            if (e.isCausedBy(VALIDATION_ERROR)) {
                log.warn("Dataspace: re-identification refused, the DID of participant {} cannot be created: {}",
                        row.getCtxId(), e.getMessage());
                throw new ConflictException(RE_IDENTIFICATION_CANNOT_MINT.build());
            }
            if (e.isCausedBy(DSP_PROVISIONING_FAILED)) {
                log.warn("Dataspace: re-identification refused, the registered address is gone: {}", e.getMessage());
                throw new ConflictException(RE_IDENTIFICATION_ADDRESS_UNKNOWN.build());
            }
            throw e;
        }
    }
}
