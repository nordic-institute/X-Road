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
package org.niis.xroad.cs.admin.api.domain;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;

import java.util.List;
import java.util.Map;

/**
 * A batch of member DIDs published by a Security Server.
 */
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class MemberDidRegistrationRequest extends Request {
    private List<Entry> entries = List.of();
    /** Results already decided before the request is stored, keyed by member; they apply to every entry of the member. */
    private Map<ClientId, Result> settledResults = Map.of();
    /** One result per entry, in the order of the entries; filled when the request is stored. */
    private List<Result> results = List.of();

    public MemberDidRegistrationRequest(Origin origin, SecurityServerId identifier, List<Entry> entries) {
        super(origin, identifier);
        this.entries = List.copyOf(entries);
    }

    @Override
    public ManagementRequestType getManagementRequestType() {
        return ManagementRequestType.MEMBER_DID_REGISTRATION_REQUEST;
    }

    /**
     * @param memberId the member identifier
     * @param did      the DID the Security Server published for the member
     */
    public record Entry(ClientId memberId, String did) {
    }

    /**
     * @param memberId the member identifier
     * @param status   the answer for the entry
     * @param reason   the cause within the status, {@code null} when the entry is stored
     */
    public record Result(ClientId memberId, Status status, String reason) {
    }

    public enum Status {
        STORED,
        FINAL_CONFLICT,
        FIXABLE_CONFLICT,
        NOT_RESOLVED
    }
}
