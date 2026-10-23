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
package org.niis.xroad.common.managementrequest;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.request.MemberDidRegEntryType;
import ee.ria.xroad.common.request.MemberDidRegRequestType;
import ee.ria.xroad.common.request.MemberDidRegResultType;

import jakarta.xml.bind.JAXBElement;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.common.managementrequest.model.MemberDidRegResponse;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestParser;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestUtil;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemberDidRegMessageTest {
    private static final String DID_A = "did:web:ss1.example.org:v1:member-a";
    private static final String DID_B = "did:web:ss1.example.org:v1:member-b";
    private static final String DID_C = "did:web:ss1.example.org:v1:member-c";

    private final SecurityServerId.Conf server = SecurityServerId.Conf.create("DEV", "COM", "222", "SS1");
    private final ClientId.Conf memberA = ClientId.Conf.create("DEV", "COM", "222");
    private final ClientId.Conf memberB = ClientId.Conf.create("DEV", "COM", "222", "SUB1");
    private final ClientId.Conf memberC = ClientId.Conf.create("DEV", "ORG", "444");
    private final ManagementRequestBuilder builder = new ManagementRequestBuilder(memberA,
            ClientId.Conf.create("DEV", "COM", "MGMT", "MANAGEMENT"));

    @Test
    void requestWithSeveralEntriesRoundTripsThroughBuilderAndParserInOrder() throws Exception {
        var request = builder.buildMemberDidRegRequest(server, entries());

        MemberDidRegRequestType parsed = ManagementRequestParser.parse(request,
                ManagementRequestType.MEMBER_DID_REGISTRATION_REQUEST.getServiceCode());

        assertThat(parsed.getServer()).isEqualTo(server);
        assertThat(parsed.getEntry()).extracting(MemberDidRegEntryType::getMember)
                .containsExactly(memberA, memberB, memberC);
        assertThat(parsed.getEntry()).extracting(MemberDidRegEntryType::getDid)
                .containsExactly(DID_A, DID_B, DID_C);
        assertThat(parsed.getRequestId()).isNull();
    }

    @Test
    void responseCarriesRequestIdAndOneResultPerEntryInRequestOrder() throws Exception {
        var request = builder.buildMemberDidRegRequest(server, entries());
        var results = List.of(
                result(memberA, "STORED", null),
                result(memberB, "FIXABLE_CONFLICT", "REJECTED_REASON"),
                result(memberC, "NOT_RESOLVED", "UNRESOLVED_REASON"));

        var responseMessage = ManagementRequestUtil.toResponse(request, 413,
                resultElements(results));
        var response = ManagementRequestSender.toMemberDidRegResponse(responseMessage);

        assertThat(response.requestId()).isEqualTo(413);
        assertThat(response.results()).containsExactly(
                new MemberDidRegResponse.Result(memberA, "STORED", null),
                new MemberDidRegResponse.Result(memberB, "FIXABLE_CONFLICT", "REJECTED_REASON"),
                new MemberDidRegResponse.Result(memberC, "NOT_RESOLVED", "UNRESOLVED_REASON"));
        assertThat(ManagementRequestSender.getRequestId(responseMessage)).isEqualTo(413);
    }

    @Test
    void responseWithoutExtraElementsAnswersRequestIdOnly() throws Exception {
        var request = builder.buildMemberDidRegRequest(server, entries());

        var responseMessage = ManagementRequestUtil.toResponse(request, 7);
        var response = ManagementRequestSender.toMemberDidRegResponse(responseMessage);

        assertThat(response.requestId()).isEqualTo(7);
        assertThat(response.results()).isEmpty();
    }

    private LinkedHashMap<ClientId.Conf, String> entries() {
        var entries = new LinkedHashMap<ClientId.Conf, String>();
        entries.put(memberA, DID_A);
        entries.put(memberB, DID_B);
        entries.put(memberC, DID_C);
        return entries;
    }

    private static List<JAXBElement<?>> resultElements(List<MemberDidRegResultType> results) {
        return results.stream().<JAXBElement<?>>map(ManagementRequestUtil::memberDidRegResult).toList();
    }

    private static MemberDidRegResultType result(ClientId.Conf member, String status, String reason) {
        var result = new MemberDidRegResultType();
        result.setMember(member);
        result.setStatus(status);
        result.setReason(reason);
        return result;
    }
}
