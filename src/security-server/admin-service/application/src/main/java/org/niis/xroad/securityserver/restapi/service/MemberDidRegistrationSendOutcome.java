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

import java.util.List;

/**
 * Outcome of sending a member DID registration request to the Central Server.
 *
 * @param status    {@link Status#ANSWERED} when the Central Server answered, {@link Status#SEND_FAILURE} otherwise
 * @param requestId request id in the Central Server database, set only when answered
 * @param entries   one record per request entry in request order, empty when sending failed
 * @param cause     send failure cause, set only when sending failed
 */
public record MemberDidRegistrationSendOutcome(Status status, Integer requestId, List<Entry> entries, Exception cause) {

    public MemberDidRegistrationSendOutcome {
        entries = List.copyOf(entries);
    }

    public enum Status {
        ANSWERED,
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
        return new MemberDidRegistrationSendOutcome(Status.ANSWERED, requestId, entries, null);
    }

    public static MemberDidRegistrationSendOutcome sendFailure(Exception cause) {
        return new MemberDidRegistrationSendOutcome(Status.SEND_FAILURE, null, List.of(), cause);
    }
}
