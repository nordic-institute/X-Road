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

import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.ds.identity.DidAuthority;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FINAL_CONFLICT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FIXABLE_CONFLICT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_NOT_RESOLVED;

/**
 * The classification of a refused {@code systemDidReg} request: one of three outcome classes and the reason
 * within it. Every way a request can be refused is mapped here, so the pre-transaction service, the handler and
 * the certificate check classify the same cause the same way.
 *
 * @param outcomeClass the class of the answer
 * @param reason       the cause within the class
 */
record SystemDidRegistrationOutcome(OutcomeClass outcomeClass, String reason) {

    static final String CERTIFICATE_OF_ANOTHER_SERVER = "CERTIFICATE_OF_ANOTHER_SERVER";
    static final String OWNER_CHANGED = "OWNER_CHANGED";
    static final String RESOLVER_UNAVAILABLE = "RESOLVER_UNAVAILABLE";

    enum OutcomeClass {
        FINAL_CONFLICT,
        FIXABLE_CONFLICT,
        NOT_RESOLVED
    }

    /**
     * @param rejection         why the published value is refused
     * @param registeredAddress the registered address of the server, as stored
     * @return the outcome; a non-ASCII host is fixable when the registered address is the non-ASCII one
     */
    static SystemDidRegistrationOutcome of(ParticipantValueRejection rejection, String registeredAddress) {
        var outcomeClass = switch (rejection) {
            case NOT_CANONICAL, DID_TOO_LONG, WRONG_SCHEME, DSP_BASE_URL_NOT_HTTPS, MALFORMED_DSP_BASE_URL,
                    FOREIGN_SYSTEM_DID, UNDECODABLE_DID, DID_OF_OTHER_MEMBER -> OutcomeClass.FINAL_CONFLICT;
            case HOST_DIFFERS, UNPARSEABLE_REGISTERED_ADDRESS, VALUE_DIFFERS, DID_HELD_BY_ANOTHER_SERVER, MEMBER_NOT_SERVED ->
                    OutcomeClass.FIXABLE_CONFLICT;
            case NON_ASCII_HOST -> isNonAscii(registeredAddress) ? OutcomeClass.FIXABLE_CONFLICT : OutcomeClass.FINAL_CONFLICT;
        };
        return new SystemDidRegistrationOutcome(outcomeClass, rejection.name());
    }

    static SystemDidRegistrationOutcome certificateOfAnotherServer() {
        return new SystemDidRegistrationOutcome(OutcomeClass.FINAL_CONFLICT, CERTIFICATE_OF_ANOTHER_SERVER);
    }

    static SystemDidRegistrationOutcome ownerChanged() {
        return new SystemDidRegistrationOutcome(OutcomeClass.FIXABLE_CONFLICT, OWNER_CHANGED);
    }

    static SystemDidRegistrationOutcome notResolved(DidResolution resolution) {
        var reason = resolution.reason() == null ? RESOLVER_UNAVAILABLE : resolution.reason().name();
        return new SystemDidRegistrationOutcome(OutcomeClass.NOT_RESOLVED, reason);
    }

    static SystemDidRegistrationOutcome resolverUnavailable() {
        return new SystemDidRegistrationOutcome(OutcomeClass.NOT_RESOLVED, RESOLVER_UNAVAILABLE);
    }

    RuntimeException toException() {
        return switch (outcomeClass) {
            case FINAL_CONFLICT -> new ConflictException(MR_DID_FINAL_CONFLICT.build(reason));
            case FIXABLE_CONFLICT -> new ConflictException(MR_DID_FIXABLE_CONFLICT.build(reason));
            case NOT_RESOLVED -> new BadRequestException(MR_DID_NOT_RESOLVED.build(reason));
        };
    }

    private static boolean isNonAscii(String registeredAddress) {
        return registeredAddress != null && !DidAuthority.isAscii(registeredAddress);
    }
}
