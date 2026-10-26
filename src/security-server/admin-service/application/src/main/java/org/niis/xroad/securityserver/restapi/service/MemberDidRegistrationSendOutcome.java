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

import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.List;

/**
 * Outcome of sending a member DID registration request to the Central Server.
 *
 * @param status    {@link Status#ANSWERED} when the Central Server answered, {@link Status#REQUEST_TOO_LARGE} when it
 *                  refused the whole batch as too large, {@link Status#SEND_FAILURE} otherwise
 * @param requestId request id in the Central Server database, set only when answered
 * @param entries   one record per request entry in request order, empty unless answered
 * @param cause     send failure cause, set only when sending failed
 * @param faultCode code of the fault the Central Server answered with, derived from the cause: the code of an
 *                  {@link XrdRuntimeException} cause, null otherwise
 */
public record MemberDidRegistrationSendOutcome(Status status, Integer requestId, List<Entry> entries, Exception cause,
                                               String faultCode) {

    public MemberDidRegistrationSendOutcome {
        entries = List.copyOf(entries);
    }

    public enum Status {
        ANSWERED,
        REQUEST_TOO_LARGE,
        SEND_FAILURE
    }

    /**
     * Result of one request entry.
     *
     * @param member the member the entry was for
     * @param status status name reported by the Central Server
     * @param reason reason reported by the Central Server, null when absent
     */
    public record Entry(ClientId member, String status, String reason) {
    }

    public static MemberDidRegistrationSendOutcome answered(int requestId, List<Entry> entries) {
        return new MemberDidRegistrationSendOutcome(Status.ANSWERED, requestId, entries, null, null);
    }

    public static MemberDidRegistrationSendOutcome requestTooLarge() {
        return new MemberDidRegistrationSendOutcome(Status.REQUEST_TOO_LARGE, null, List.of(), null, null);
    }

    public static MemberDidRegistrationSendOutcome sendFailure(Exception cause) {
        var faultCode = cause instanceof XrdRuntimeException fault ? fault.getCode() : null;
        return new MemberDidRegistrationSendOutcome(Status.SEND_FAILURE, null, List.of(), cause, faultCode);
    }
}
