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

package org.niis.xroad.cs.admin.core.entity;

import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Entry;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Result;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.core.entity.converter.DidBatchJson;

import java.util.List;
import java.util.stream.IntStream;

import static org.niis.xroad.cs.admin.core.entity.MemberDidRegistrationRequestEntity.DISCRIMINATOR_VALUE;

@Entity
@NoArgsConstructor
@DiscriminatorValue(DISCRIMINATOR_VALUE)
public class MemberDidRegistrationRequestEntity extends RequestEntity {

    public static final String DISCRIMINATOR_VALUE = "MemberDidRegistrationRequest";

    @Column(name = "request_data", columnDefinition = "TEXT")
    @Convert(converter = DidBatchJson.EntriesConverter.class)
    @Immutable
    @Getter
    private List<DidBatchJson.Entry> requestData;

    @Column(name = "response_data", columnDefinition = "TEXT")
    @Convert(converter = DidBatchJson.ResultsConverter.class)
    @Immutable
    @Getter
    private List<DidBatchJson.Result> responseData;

    /**
     * @param entries the entries as received
     * @param results one result per entry, in the order of the entries
     */
    public MemberDidRegistrationRequestEntity(Origin origin, SecurityServerId identifier, List<Entry> entries, List<Result> results) {
        super(origin, identifier, null);
        this.requestData = entries.stream()
                .map(entry -> new DidBatchJson.Entry(entry.memberId().asEncodedId(), entry.did()))
                .toList();
        this.responseData = IntStream.range(0, results.size())
                .mapToObj(i -> new DidBatchJson.Result(entries.get(i).did(), results.get(i).status(), results.get(i).reason()))
                .toList();
    }

    @Override
    public ManagementRequestType getManagementRequestType() {
        return ManagementRequestType.MEMBER_DID_REGISTRATION_REQUEST;
    }
}
