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

package org.niis.xroad.cs.admin.core.service.managementrequest;

import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionOutcome;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Result;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.core.service.managementrequest.SystemDidRegistrationOutcome.OutcomeClass;

import java.util.EnumSet;
import java.util.Set;

import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DSP_BASE_URL_NOT_HTTPS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.FOREIGN_SYSTEM_DID;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.MALFORMED_DSP_BASE_URL;

/**
 * The answer for one {@code memberDidReg} entry: a status and the reason within it. Every cause an entry can
 * be refused for is classified through {@link SystemDidRegistrationOutcome}, so the member and the
 * {@code systemDidReg} requests answer the same cause with the same class.
 */
final class MemberDidRegistrationOutcome {

    static final String CONFLICTING_DUPLICATE = "CONFLICTING_DUPLICATE";
    static final String SYSTEM_VALUES_NOT_STORED = "SYSTEM_VALUES_NOT_STORED";

    /** Reasons the validator gives only for the SYSTEM pair; a member entry cannot get them. */
    static final Set<ParticipantValueRejection> SYSTEM_PAIR_ONLY =
            EnumSet.of(DSP_BASE_URL_NOT_HTTPS, MALFORMED_DSP_BASE_URL, FOREIGN_SYSTEM_DID);

    private MemberDidRegistrationOutcome() {
    }

    static Result stored(ClientId member) {
        return new Result(member, Status.STORED, null);
    }

    /**
     * @param member            the member of the entry
     * @param rejection         why the DID is refused
     * @param registeredAddress the registered address of the server, as stored
     * @return the answer for the entry
     * @throws IllegalArgumentException when the reason can only be given for the SYSTEM pair
     */
    static Result rejected(ClientId member, ParticipantValueRejection rejection, String registeredAddress) {
        if (SYSTEM_PAIR_ONLY.contains(rejection)) {
            throw new IllegalArgumentException("Reason " + rejection + " cannot occur for a member DID");
        }
        return of(member, SystemDidRegistrationOutcome.of(rejection, registeredAddress));
    }

    static Result conflictingDuplicate(ClientId member) {
        return of(member, new SystemDidRegistrationOutcome(OutcomeClass.FINAL_CONFLICT, CONFLICTING_DUPLICATE));
    }

    static Result systemValuesNotStored(ClientId member) {
        return of(member, new SystemDidRegistrationOutcome(OutcomeClass.FIXABLE_CONFLICT, SYSTEM_VALUES_NOT_STORED));
    }

    static Result ownerChanged(ClientId member) {
        return of(member, SystemDidRegistrationOutcome.ownerChanged());
    }

    /**
     * @throws IllegalArgumentException when the resolution is a resolved one, which is no refusal
     */
    static Result notResolved(ClientId member, DidResolution resolution) {
        if (resolution.outcome() == DidResolutionOutcome.RESOLVED) {
            throw new IllegalArgumentException("A resolved DID is not a refusal");
        }
        return of(member, SystemDidRegistrationOutcome.notResolved(resolution));
    }

    static Result resolverUnavailable(ClientId member) {
        return of(member, SystemDidRegistrationOutcome.resolverUnavailable());
    }

    private static Result of(ClientId member, SystemDidRegistrationOutcome outcome) {
        var status = switch (outcome.outcomeClass()) {
            case FINAL_CONFLICT -> Status.FINAL_CONFLICT;
            case FIXABLE_CONFLICT -> Status.FIXABLE_CONFLICT;
            case NOT_RESOLVED -> Status.NOT_RESOLVED;
        };
        return new Result(member, status, outcome.reason());
    }
}
