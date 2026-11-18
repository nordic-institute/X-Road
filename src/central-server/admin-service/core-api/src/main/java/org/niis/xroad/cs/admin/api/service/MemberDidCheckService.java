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
package org.niis.xroad.cs.admin.api.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.niis.xroad.cs.admin.api.domain.DidCheck;

/**
 * Tells an administrator whether a proposed DID of a member on a Security Server would be accepted as a
 * replacement and resolves. Read-only: stores nothing.
 */
public interface MemberDidCheckService {

    /**
     * Applies the rules of a member DID replacement without writing, and resolves the DID through the Issuer
     * Service only when the replacement would not be refused. A DID equal to the stored one is resolved too.
     * The check does not limit which host the resolver contacts.
     *
     * @param serverId  the identifier of the server
     * @param memberId  the member whose DID is checked
     * @param memberDid the proposed DID
     * @return the result of the check
     * @throws org.niis.xroad.common.exception.NotFoundException               when the member does not exist
     * @throws org.niis.xroad.common.exception.SecurityServerNotFoundException when the server does not exist
     */
    DidCheck check(SecurityServerId serverId, ClientId memberId, String memberDid);
}
