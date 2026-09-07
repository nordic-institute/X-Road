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
package org.niis.xroad.serverconf.impl.participant;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_IDENTIFIER_MISMATCH;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED;

/**
 * Verifies a bound {@code ds_participant} row against a fresh re-derivation, per XRDADR-41's
 * derive-then-bind decision: the bound row is authoritative, and a difference from a fresh
 * derivation must never be resolved by silently overwriting it.
 */
@UtilityClass
public class ParticipantBindingCheck {

    /**
     * Re-derives the bound participant's ctx-id and DID and compares them to the bound row.
     * Never mutates {@code bound}.
     *
     * @param bound the previously bound participant row
     * @param ssHost the Security Server's current public address, as {@code host} or {@code host:port}
     * @throws XrdRuntimeException with {@code DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED} and metadata
     *         {@code [boundSchemeVersion, supportedSchemeVersion]} if the row was bound under a scheme
     *         version this check does not implement, or with {@code DSP_PARTICIPANT_IDENTIFIER_MISMATCH}
     *         and metadata {@code [boundCtxId, boundDid, derivedCtxId, derivedDid]} if the re-derived
     *         ctx-id or DID differs from the bound row
     */
    public static void verify(DsParticipantEntity bound, String ssHost) {
        if (!ParticipantIdentifierScheme.SCHEME_VERSION.equals(bound.getSchemeVersion())) {
            throw XrdRuntimeException.systemException(DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED)
                    .metadataItems(bound.getSchemeVersion(), ParticipantIdentifierScheme.SCHEME_VERSION)
                    .details(("participant '%s' was bound under scheme version '%s', but only '%s' is supported; "
                            + "the bound identifiers cannot be verified against the '%s' derivation")
                            .formatted(bound.getCtxId(), bound.getSchemeVersion(),
                                    ParticipantIdentifierScheme.SCHEME_VERSION, ParticipantIdentifierScheme.SCHEME_VERSION))
                    .build();
        }

        Derived derived = derive(bound, ssHost);

        if (!bound.getCtxId().equals(derived.ctxId()) || !bound.getDid().equals(derived.did())) {
            throw XrdRuntimeException.systemException(DSP_PARTICIPANT_IDENTIFIER_MISMATCH)
                    .metadataItems(bound.getCtxId(), bound.getDid(), derived.ctxId(), derived.did())
                    .details(("bound participant identifier no longer matches derivation: "
                            + "bound ctx-id='%s' did='%s', derived ctx-id='%s' did='%s'")
                            .formatted(bound.getCtxId(), bound.getDid(), derived.ctxId(), derived.did()))
                    .build();
        }
    }

    private static Derived derive(DsParticipantEntity bound, String ssHost) {
        return switch (bound.getParticipantType()) {
            case MEMBER -> {
                ClientId member = bound.getMemberIdentifier();
                yield new Derived(
                        ParticipantIdentifierScheme.memberCtxId(member),
                        ParticipantIdentifierScheme.memberDid(member, ssHost));
            }
            case SYSTEM -> new Derived(
                    ParticipantIdentifierScheme.SYSTEM_SEGMENT,
                    ParticipantIdentifierScheme.systemDid(ssHost));
        };
    }

    private record Derived(String ctxId, String did) {
    }

}
