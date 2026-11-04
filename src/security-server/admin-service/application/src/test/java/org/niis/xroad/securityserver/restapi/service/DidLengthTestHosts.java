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

import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;

final class DidLengthTestHosts {

    private static final int MAX_LABEL_LENGTH = 60;

    private DidLengthTestHosts() {
    }

    /**
     * @param member    the member whose DID is measured
     * @param didLength the exact length the member's DID must have
     * @return a valid host name that makes the member's DID exactly {@code didLength} characters long
     */
    static String hostForMemberDidLength(ClientId member, int didLength) {
        var shortHost = "a";
        var remaining = didLength - ParticipantIdentifierScheme.memberDid(member, shortHost).toString().length()
                + shortHost.length();
        var host = new StringBuilder();
        while (remaining > MAX_LABEL_LENGTH + 1) {
            host.append("a".repeat(MAX_LABEL_LENGTH)).append('.');
            remaining -= MAX_LABEL_LENGTH + 1;
        }
        return host.append("a".repeat(remaining)).toString();
    }
}
