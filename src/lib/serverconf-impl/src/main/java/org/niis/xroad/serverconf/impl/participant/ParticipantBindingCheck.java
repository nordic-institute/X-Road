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

import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.DecodedParticipant;
import org.niis.xroad.ds.identity.MemberParticipant;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.ds.identity.SystemParticipant;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED;
import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * Checks that a bound {@code ds_participant} row was bound under an identifier scheme version this
 * server implements and that its stored columns agree with each other. The bound row is authoritative:
 * its identifiers are used as stored and never re-minted from the current registered address, so the DID's
 * host and port are not compared with anything.
 */
@UtilityClass
public class ParticipantBindingCheck {

    /**
     * Checks the scheme version of the bound row, then that the member and context segments of its DID
     * match the row's participant type, member identifier and ctx-id. Never mutates {@code bound}.
     *
     * @param bound the previously bound participant row
     * @throws XrdRuntimeException with {@code DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED} and metadata
     *         {@code [boundSchemeVersion, supportedSchemeVersion]} if the row was bound under a scheme
     *         version this check does not implement; with {@code VALIDATION_ERROR} if the DID encodes another
     *         participant than the row's member identifier or ctx-id; with {@code INVALID_ENCODED_ID} if the
     *         DID cannot be decoded
     */
    public static void verify(DsParticipantEntity bound) {
        if (!ParticipantIdentifierScheme.SCHEME_VERSION.equals(bound.getSchemeVersion())) {
            throw XrdRuntimeException.systemException(DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED)
                    .metadataItems(bound.getSchemeVersion(), ParticipantIdentifierScheme.SCHEME_VERSION)
                    .details(("participant '%s' was bound under scheme version '%s', but only '%s' is supported; "
                            + "the bound identifiers use an unsupported scheme version")
                            .formatted(bound.getCtxId(), bound.getSchemeVersion(),
                                    ParticipantIdentifierScheme.SCHEME_VERSION))
                    .build();
        }

        var expectedCtxId = switch (bound.getParticipantType()) {
            case MEMBER -> ParticipantIdentifierScheme.memberCtxId(bound.getMemberIdentifier());
            case SYSTEM -> ParticipantIdentifierScheme.SYSTEM_SEGMENT;
        };
        if (!expectedCtxId.equals(bound.getCtxId()) || !expectedCtxId.equals(ctxIdOf(bound))) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "bound participant is inconsistent: %s participant has ctx-id '%s' and did '%s', expected ctx-id '%s'",
                    bound.getParticipantType(), bound.getCtxId(), bound.getDid(), expectedCtxId);
        }
    }

    private static String ctxIdOf(DsParticipantEntity bound) {
        DecodedParticipant decoded = ParticipantIdentifierScheme.decodeDid(bound.getDid());
        return switch (decoded) {
            case MemberParticipant member -> ParticipantIdentifierScheme.memberCtxId(member.member());
            case SystemParticipant _ -> ParticipantIdentifierScheme.SYSTEM_SEGMENT;
        };
    }

}
