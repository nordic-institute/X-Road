/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.cs.test.glue;

import io.cucumber.java.en.Step;
import org.niis.xroad.cs.openapi.model.ClientDto;
import org.niis.xroad.cs.openapi.model.ClientIdDto;
import org.niis.xroad.cs.openapi.model.MemberGlobalGroupDto;
import org.niis.xroad.cs.test.api.FeignMembersApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.apache.commons.lang3.StringUtils.split;
import static org.niis.xroad.cs.openapi.model.XRoadIdDto.TypeEnum.MEMBER;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class MembersApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignMembersApi membersApi;

    @Step("new member {string} is added")
    public void newMemberCSEEMemberEEIsCreated(String memberId) {
        final String[] idParts = split(memberId, ':');

        final ClientIdDto clientIdDto = new ClientIdDto()
                .memberClass(idParts[1])
                .memberCode(idParts[2]);
        clientIdDto.setType(MEMBER);
        clientIdDto.setInstanceId(idParts[0]);

        final ClientDto dto = new ClientDto()
                .memberName("Member name for " + memberId)
                .xroadId(clientIdDto);

        final ResponseEntity<ClientDto> response = membersApi.addMember(dto);

        validate(response)
                .assertion(equalsStatusCodeAssertion(CREATED))
                .execute();
    }

    @Step("member {string} is not in global group {string}")
    public void memberCSEEMemberIsNotInGlobalGroupSecurityServerOwners(String memberId, String globalGroupCode) {
        final ResponseEntity<Set<MemberGlobalGroupDto>> response = membersApi.getMemberGlobalGroups(memberId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(0, "body.?[groupCode=='" + globalGroupCode + "'].size()",
                        "Verify groups do not contain " + globalGroupCode))
                .execute();

    }

    @Step("member {string} is in global group {string}")
    public void memberCSEEMemberIsInGlobalGroupSecurityServerOwners(String memberId, String globalGroupCode) {
        final ResponseEntity<Set<MemberGlobalGroupDto>> response = membersApi.getMemberGlobalGroups(memberId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1, "body.?[groupCode=='" + globalGroupCode + "'].size()",
                        "Verify groups contains " + globalGroupCode))
                .execute();
    }

}
