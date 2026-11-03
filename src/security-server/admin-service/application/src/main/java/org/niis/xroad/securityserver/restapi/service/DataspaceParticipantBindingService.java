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

import com.apicatalog.did.Did;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DidMintResult.Rejected;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Writes the {@code ds_participant} rows that bind a member's or the Security Server's own SYSTEM
 * data space identity, per XRDADR-41's derive-then-bind decision. The only writer of that table.
 *
 * <p>Binding is level-triggered: each provisioning pass binds whichever hosted members, and the
 * SYSTEM identity, still lack a row, so an identity is bound once the caller allows it and stays
 * bound from then on. A bound row is authoritative and is never rewritten.
 *
 * <p>Binding comes before provisioning: the caller binds an identity first and provisions only the
 * identities this service reports as bound. Deciding when binding is allowed (readiness, a known
 * registered address, a registered authentication certificate) is the caller's job.
 *
 * <p>The caller passes, with each identity, the DID it checked against the identity hub. A fresh mint
 * that no longer yields that DID, for example because the registered address changed in between, binds nothing; the
 * identity is retried on a later pass, so a binding never freezes a DID the caller did not check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataspaceParticipantBindingService {

    private final DsParticipantRepository dsParticipantRepository;
    private final DataspaceDidAuthority didAuthority;

    /**
     * Binds the derived ctx-id and DID of every given member that has no row yet. Existing rows are
     * left untouched. Never throws: a member that cannot be bound now, including one whose DID minting is
     * rejected or no longer yields the checked DID, is left out of the result and retried on a later pass,
     * and does not stop the other members from being bound.
     *
     * @param checkedDids the hosted members to bind, each with the DID the caller checked for it
     * @return the given members that hold an active binding row afterwards, in the map's iteration order
     */
    public List<ClientId> bindMembersIfAbsent(Map<ClientId, Did> checkedDids) {
        List<ClientId> bound = new ArrayList<>();
        for (var entry : checkedDids.entrySet()) {
            var member = entry.getKey();
            try {
                if (bindIfAbsent(member, entry.getValue())) {
                    bound.add(member);
                }
            } catch (Exception e) {
                log.warn("Data space: could not bind participant identity for member {}", member, e);
            }
        }
        return bound;
    }

    /**
     * Binds the derived SYSTEM ctx-id and DID unless a SYSTEM row exists. An existing row is left
     * untouched. Never throws: a SYSTEM identity that cannot be bound now, including one whose DID minting
     * no longer yields the checked DID, is reported as not bound and retried on a later pass.
     *
     * @param checkedDid the SYSTEM DID the caller checked
     * @return whether an active SYSTEM binding row exists afterwards
     */
    public boolean bindSystemIfAbsent(Did checkedDid) {
        try {
            return bindUnlessPresent(dsParticipantRepository.findSystemParticipant(), () -> bindSystem(checkedDid));
        } catch (Exception e) {
            log.warn("Data space: could not bind participant identity for the SYSTEM context", e);
            return false;
        }
    }

    private boolean bindIfAbsent(ClientId member, Did checkedDid) {
        return bindUnlessPresent(dsParticipantRepository.findByMemberIdentifier(member), () -> bindMember(member, checkedDid));
    }

    private static boolean bindUnlessPresent(Optional<DsParticipantEntity> existing, BooleanSupplier bind) {
        if (existing.isPresent()) {
            return existing.get().getState() == ParticipantState.ACTIVE;
        }
        return bind.getAsBoolean();
    }

    private boolean bindMember(ClientId member, Did checkedDid) {
        var minted = bindableDid(didAuthority.mintMemberDid(member), checkedDid, "member " + member);
        if (minted.isEmpty()) {
            return false;
        }
        var ctxId = ParticipantIdentifierScheme.memberCtxId(member);
        dsParticipantRepository.bindMemberParticipant(member, ctxId, minted.get().toString());
        log.info("Dataspace: bound participant identity for member {} (ctx-id '{}', DID '{}')", member, ctxId,
                minted.get());
        return true;
    }

    private boolean bindSystem(Did checkedDid) {
        var minted = bindableDid(didAuthority.mintSystemDid(), checkedDid, "the SYSTEM context");
        if (minted.isEmpty()) {
            return false;
        }
        var ctxId = ParticipantIdentifierScheme.SYSTEM_SEGMENT;
        dsParticipantRepository.bindSystemParticipant(ctxId, minted.get().toString());
        log.info("Dataspace: bound participant identity for the SYSTEM context (ctx-id '{}', DID '{}')", ctxId,
                minted.get());
        return true;
    }

    private static Optional<Did> bindableDid(DidMintResult result, Did checkedDid, String subject) {
        if (result instanceof Rejected rejected) {
            log.warn("Dataspace: not binding {}, no DID can be minted ({}): {}", subject, rejected.reason(),
                    rejected.detail());
        }
        var minted = result.did();
        if (minted.isPresent() && !minted.get().equals(checkedDid)) {
            log.info("Dataspace: not binding {} yet, the DID minted now ('{}') differs from the checked DID '{}'; "
                    + "retrying on the next pass", subject, minted.get(), checkedDid);
            return Optional.empty();
        }
        return minted;
    }
}
