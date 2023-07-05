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

import feign.FeignException;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.openapi.model.ClientDto;
import org.niis.xroad.cs.openapi.model.MemberAddDto;
import org.niis.xroad.cs.openapi.model.MemberGlobalGroupDto;
import org.niis.xroad.cs.openapi.model.MemberNameDto;
import org.niis.xroad.cs.openapi.model.NewMemberIdDto;
import org.niis.xroad.cs.openapi.model.SecurityServerDto;
import org.niis.xroad.cs.openapi.model.SubsystemDto;
import org.niis.xroad.cs.test.api.FeignMembersApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.apache.commons.lang3.StringUtils.split;
import static org.junit.Assert.fail;
import static org.niis.xroad.cs.openapi.model.XRoadIdDto.TypeEnum.MEMBER;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class MembersApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignMembersApi membersApi;

    @Step("new member {string} is added")
    public void newMemberIsCreated(String memberId) {
        newMemberAddedWithName(memberId, "Member name for " + memberId);
    }

    @Step("new member {string} is added with name {string}")
    public void newMemberAddedWithName(String memberId, String name) {
        final String[] idParts = split(memberId, ':');

        final var clientIdDto = new NewMemberIdDto()
                .memberClass(idParts[1])
                .memberCode(idParts[2]);

        final MemberAddDto dto = new MemberAddDto()
                .memberName(name)
                .memberId(clientIdDto);

        final ResponseEntity<ClientDto> response = membersApi.addMember(dto);
        validateMemberResponse(response, CREATED, memberId, name);
    }

    @Step("adding new member {string} should fail")
    public void addingNewMemberShouldFail(String memberId) {
        try {
            newMemberIsCreated(memberId);
            fail("should fail");
        } catch (FeignException exception) {
            validate(exception)
                    .assertion(equalsAssertion(CONFLICT.value(), "status"))
                    .execute();
        }
    }

    @Step("member {string} is not in global group {string}")
    public void memberIsNotInGlobalGroup(String memberId, String globalGroupCode) {
        final ResponseEntity<List<MemberGlobalGroupDto>> response = membersApi.getMemberGlobalGroups(memberId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(0, "body.?[groupCode=='" + globalGroupCode + "'].size()",
                        "Verify groups do not contain " + globalGroupCode))
                .execute();
    }

    @Step("member {string} is in global group {string}")
    public void memberIsInGlobalGroup(String memberId, String globalGroupCode) {
        final ResponseEntity<List<MemberGlobalGroupDto>> response = membersApi.getMemberGlobalGroups(memberId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1, "body.?[groupCode=='" + globalGroupCode + "'].size()",
                        "Verify groups contains " + globalGroupCode))
                .execute();
    }

    @Step("member {string} subsystems contains {string}")
    public void memberSubsystemsContainsSubsystem(String memberId, String subsystemCode) {
        final ResponseEntity<List<SubsystemDto>> response = membersApi.getSubsystems(memberId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1, "body.?[subsystemId.subsystemCode == '" + subsystemCode + "'].size"))
                .execute();
    }

    @Step("member {string} subsystems does not contain {string}")
    public void memberSubsystemsNotContainsSubsystem(String memberId, String subsystemCode) {
        final ResponseEntity<List<SubsystemDto>> response = membersApi.getSubsystems(memberId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(0, "body.?[subsystemId.subsystemCode == '" + subsystemCode + "'].size"))
                .execute();
    }

    @Step("user can retrieve member {string} details")
    public void userCanRetrieveMemberDetails(String memberId) {
        final ResponseEntity<ClientDto> response = membersApi.getMember(memberId);

        validateMemberResponse(response, OK, memberId, "Member name for " + memberId);
    }

    @Step("user deletes member {string}")
    public void userCanDeleteMember(String memberId) {
        final ResponseEntity<Void> response = membersApi.deleteMember(memberId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(NO_CONTENT))
                .execute();
    }

    @Step("user requests member {string} details")
    public void userRequestsMemberDetails(String memberId) {
        try {
            var result = membersApi.getMember(memberId);
            putStepData(StepDataKey.RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("user updates member {string} name to {string}")
    public void userUpdatesMemberName(String memberId, String memberName) {
        try {
            final var response = membersApi.updateMemberName(memberId, new MemberNameDto().memberName(memberName));
            validateMemberResponse(response, OK, memberId, memberName);
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    private void validateMemberResponse(ResponseEntity<ClientDto> response, HttpStatus status,
                                        String memberId, String memberName) {
        final String[] idParts = memberId.split(":");
        validate(response)
                .assertion(equalsStatusCodeAssertion(status))
                .assertion(equalsAssertion(memberName, "body.memberName"))
                .assertion(equalsAssertion(MEMBER, "body.clientId.type"))
                .assertion(equalsAssertion(idParts[0], "body.clientId.instanceId"))
                .assertion(equalsAssertion(idParts[1], "body.clientId.memberClass"))
                .assertion(equalsAssertion(idParts[2], "body.clientId.memberCode"))
                .execute();
    }

    @Step("member {string} name is {string}")
    public void validateMemberName(String memberId, String memberName) {
        final var response = membersApi.getMember(memberId);

        validateMemberResponse(response, OK, memberId, memberName);
    }

    @Step("member {string} owned servers contains {string}")
    public void memberOwnedServersContains(String memberId, String serverId) {
        final ResponseEntity<List<SecurityServerDto>> response = membersApi.getOwnedServers(memberId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1, "body.?[serverId.encodedId=='" + serverId + "'].size()"))
                .execute();
    }

    @Step("Owned servers list for not existing member should be empty")
    public void validateOwnedServersIsEmpty() {
        final var response = membersApi.getOwnedServers(randomMemberId(3));

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(0, "body.size()"))
                .execute();
    }

    @Step("Global groups for not existing member should be empty")
    public void validateGlobalGroupsIsEmpty() {
        final var response = membersApi.getMemberGlobalGroups(randomMemberId(3));

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(0, "body.size()"))
                .execute();
    }
}
