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

import com.nortal.test.asserts.Assertion;
import com.nortal.test.asserts.ValidationHelper;
import feign.FeignException;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.openapi.model.ClientTypeDto;
import org.niis.xroad.cs.openapi.model.GlobalGroupCodeAndDescriptionDto;
import org.niis.xroad.cs.openapi.model.GlobalGroupDescriptionDto;
import org.niis.xroad.cs.openapi.model.GlobalGroupResourceDto;
import org.niis.xroad.cs.openapi.model.GroupMemberListViewDto;
import org.niis.xroad.cs.openapi.model.GroupMembersFilterDto;
import org.niis.xroad.cs.openapi.model.GroupMembersFilterModelDto;
import org.niis.xroad.cs.openapi.model.MembersDto;
import org.niis.xroad.cs.openapi.model.PagedGroupMemberListViewDto;
import org.niis.xroad.cs.openapi.model.PagingSortingParametersDto;
import org.niis.xroad.cs.test.api.FeignGlobalGroupsApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static com.nortal.test.asserts.Assertions.notNullAssertion;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.partitioningBy;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.math.NumberUtils.createInteger;
import static org.junit.Assert.fail;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.ERROR_RESPONSE_BODY;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.RESPONSE;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.RESPONSE_STATUS;
import static org.niis.xroad.cs.test.utils.AssertionUtils.isTheListSorted;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings({"SpringJavaInjectionPointsAutowiringInspection", "checkstyle:MagicNumber", "java:S1192"})
public class GlobalGroupsStepDefs extends BaseStepDefs {

    @Autowired
    private FeignGlobalGroupsApi globalGroupsApi;

    @Step("new global group {string} with description {string} is added")
    public void newGlobalGroupIsAdded(String groupCode, String description) {
        final GlobalGroupCodeAndDescriptionDto dto = new GlobalGroupCodeAndDescriptionDto();
        dto.setCode(groupCode);
        dto.setDescription(description);

        try {
            final ResponseEntity<GlobalGroupResourceDto> response = globalGroupsApi.addGlobalGroup(dto);

            validate(response)
                    .assertion(equalsStatusCodeAssertion(CREATED))
                    .assertion(equalsAssertion(groupCode, "body.code"))
                    .assertion(equalsAssertion(description, "body.description"))
                    .assertion(equalsAssertion(0, "body.memberCount"))
                    .execute();

            putStepData(RESPONSE_STATUS, response.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("members are added to group {string}")
    public void addMemberToGlobalGroup(String groupCode, DataTable table) {
        final var members = new MembersDto();
        var identifiers = table.asMaps().stream()
                .collect(partitioningBy(map -> Boolean.parseBoolean(map.getOrDefault("$isNew", TRUE.toString())),
                        mapping(map -> map.get("$identifier"), Collectors.toList())));

        members.setItems(new LinkedList<>());
        table.asMaps().stream()
                .map(map -> map.get("$identifier"))
                .forEach(members.getItems()::add);

        try {
            final ResponseEntity<MembersDto> response = globalGroupsApi.addGlobalGroupMembers(groupCode, members);

            validate(response)
                    .assertion(equalsStatusCodeAssertion(CREATED))
                    .assertion(notNullAssertion("body.items"))
                    .assertion(equalsAssertion(identifiers.get(TRUE), "body.items"))
                    .execute();

            putStepData(RESPONSE, response);
            putStepData(RESPONSE_STATUS, response.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("global group {string} description is updated to {string}")
    public void globalGroupDescriptionIsUpdated(String groupCode, String description) {
        final GlobalGroupDescriptionDto dto = new GlobalGroupDescriptionDto()
                .description(description);

        final ResponseEntity<GlobalGroupResourceDto> response =
                globalGroupsApi.updateGlobalGroupDescription(groupCode, dto);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(description, "body.description"))
                .execute();
    }

    @Step("global group {string} description is {string}")
    public void globalGroupTestGroupDescriptionIsNewDescription(String groupCode, String description) {
        final ResponseEntity<GlobalGroupResourceDto> response = globalGroupsApi.getGlobalGroup(groupCode);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(groupCode, "body.code"))
                .assertion(equalsAssertion(description, "body.description"))
                .execute();
    }

    @Step("global group {string} has {int} member")
    @Step("global group {string} has {int} members")
    public void globalGroupTestGroupMemberCount(String groupCode, int members) {
        final ResponseEntity<GlobalGroupResourceDto> response = globalGroupsApi.getGlobalGroup(groupCode);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(groupCode, "body.code"))
                .assertion(equalsAssertion(members, "body.memberCount"))
                .execute();
    }

    @Step("global groups list contains {int} entries")
    public void globalGroupsListContainsEntries(int count) {
        final ResponseEntity<List<GlobalGroupResourceDto>> response = globalGroupsApi.findGlobalGroups();

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(count, "body.size()"))
                .execute();
    }

    @Step("global group {string} is deleted")
    public void globalGroupTestGroupIsDeleted(String code) {
        final ResponseEntity<Void> response = globalGroupsApi.deleteGlobalGroup(code);

        validate(response)
                .assertion(equalsStatusCodeAssertion(NO_CONTENT))
                .execute();
    }

    @Step("global group {string} member {string} is deleted")
    public void globalGroupMemberIsDeleted(String code, String memberIdentifier) {
        try {
            final ResponseEntity<Void> response = globalGroupsApi.deleteGlobalGroupMember(code, memberIdentifier);

            putStepData(RESPONSE, response);
            putStepData(RESPONSE_STATUS, response.getStatusCodeValue());

            validate(response)
                    .assertion(equalsStatusCodeAssertion(NO_CONTENT))
                    .execute();
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("global group {string} members list with page size {int} is queried")
    public void globalGroupMembersListIsQueriedAndContains(String groupCode, Integer pageSize) {
        final GroupMembersFilterDto filterDto = new GroupMembersFilterDto()
                .pagingSorting(new PagingSortingParametersDto().limit(pageSize));
        try {
            final ResponseEntity<PagedGroupMemberListViewDto> response = globalGroupsApi
                    .findGlobalGroupMembers(groupCode, filterDto);

            putStepData(RESPONSE, response);
            putStepData(RESPONSE_STATUS, response.getStatusCodeValue());

            validate(response)
                    .assertion(equalsStatusCodeAssertion(OK))
                    .execute();
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("global group {string} members list does not contain {string} member")
    public void globalGroupMembersListIsQueriedAndContains(String groupCode, String memberCode) {
        ResponseEntity<PagedGroupMemberListViewDto> response = getRequiredStepData(RESPONSE);
        validate(response)
                .assertion(equalsAssertion(0, "body.items.?[name=='" + memberCode + "'].size()",
                        "Global group member list contains the member"))
                .execute();
    }

    @Step("global group {string} members list is queried and validated using params")
    public void globalGroupMembersListIsQueriedAndValidatedUsingParams(String code, DataTable table) {
        for (Map<String, String> params : table.asMaps()) {
            final Boolean desc = ofNullable(params.get("$desc")).map(Boolean::valueOf).orElse(null);
            final GroupMembersFilterDto filterDto = new GroupMembersFilterDto()
                    .query(params.get("$q"))
                    .types(getTypes(params.get("$types")))
                    .instance(params.get("$instance"))
                    .memberClass(params.get("$class"))
                    .codes(ofNullable(params.get("$codes")).map(c -> List.of(c.split(","))).orElse(null))
                    .subsystems(ofNullable(params.get("$subsystems")).map(c -> List.of(c.split(","))).orElse(null));

            final PagingSortingParametersDto pagingSortingDto = new PagingSortingParametersDto()
                    .sort(params.get("$sortBy"))
                    .desc(desc);
            ofNullable(params.get("$pageSize")).ifPresent(p -> pagingSortingDto.limit(createInteger(p)));
            ofNullable(params.get("$page")).ifPresent(p -> pagingSortingDto.offset(createInteger(p) - 1));
            filterDto.setPagingSorting(pagingSortingDto);

            final ResponseEntity<PagedGroupMemberListViewDto> response = globalGroupsApi
                    .findGlobalGroupMembers(code, filterDto);

            final ValidationHelper validations = validate(response)
                    .assertion(equalsStatusCodeAssertion(OK))
                    .assertion(equalsAssertion(createInteger(params.get("$itemsInPage")), "body.items.size()"))
                    .assertion(equalsAssertion(createInteger(params.get("$itemsInPage")), "body.pagingMetadata.items"))
                    .assertion(equalsAssertion(createInteger(params.get("$total")), "body.pagingMetadata.totalItems"));
            if (isNotBlank(params.get("$sortFieldExp"))) {
                validations.assertion(new Assertion.Builder()
                        .message("Verify items are sorted")
                        .expression("=")
                        .actualValue(isTheListSorted(Collections.singletonList(response.getBody().getItems()), TRUE.equals(desc),
                                params.get("$sortFieldExp")))
                        .expectedValue(true)
                        .build());
            }
            validations.execute();
        }
    }

    private List<ClientTypeDto> getTypes(String types) {
        return ofNullable(types)
                .map(t -> t.split(","))
                .map(t -> Arrays.stream(t).map(ClientTypeDto::fromValue).collect(Collectors.toList()))
                .orElse(null);
    }

    private Optional<Integer> resolveGlobalGroupMemberId(String groupCode, String memberName) {
        final GroupMembersFilterDto filterDto = new GroupMembersFilterDto();

        final PagingSortingParametersDto pagingSortingDto = new PagingSortingParametersDto()
                .limit(10)
                .offset(0)
                .desc(false);

        filterDto.setPagingSorting(pagingSortingDto);

        final ResponseEntity<PagedGroupMemberListViewDto> response = globalGroupsApi
                .findGlobalGroupMembers(groupCode, filterDto);

        return response.getBody().getItems().stream()
                .filter(member -> memberName.equals(member.getName()))
                .map(GroupMemberListViewDto::getId)
                .map(Integer::valueOf)
                .findFirst();
    }

    @Step("global group {string} has filter model as follows")
    public void globalGroupHasFilterModelAsFollows(String groupCode, DataTable dataTable) {
        final ResponseEntity<GroupMembersFilterModelDto> response =
                globalGroupsApi.getGroupMembersFilterModel(groupCode);

        final Map<String, String> values = dataTable.asMap();

        final List<String> instances = toList(values.get("$instances"));
        final List<String> memberClasses = toList(values.get("$memberClasses"));
        final List<String> codes = toList(values.get("$codes"));
        final List<String> subsystems = toList(values.get("$subsystems"));

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(collectionEqualsAssertion(instances, "body.instances"))
                .assertion(collectionEqualsAssertion(memberClasses, "body.memberClasses"))
                .assertion(collectionEqualsAssertion(codes, "body.codes"))
                .assertion(collectionEqualsAssertion(subsystems, "body.subsystems"))
                .execute();
    }

    private List<String> toList(String value) {
        return ofNullable(value).map(s -> List.of(s.split(","))).orElse(List.of());
    }

    @Step("deleting not existing group fails with status code {int} and error code {string}")
    public void deletingNotExistingGroup(int status, String error) {
        try {
            globalGroupsApi.deleteGlobalGroup("random");
            fail("should fail");
        } catch (FeignException feignException) {
            validateErrorResponse(status, error, feignException);
        }
    }

    @Step("deleting global group {string} fails with status code {int} and error code {string}")
    public void deletingGlobalGroupFailsWithStatusCodeAndErrorCode(String groupCode, int status, String error) {
        try {
            globalGroupsApi.deleteGlobalGroup(groupCode);
            fail("should fail");
        } catch (FeignException feignException) {
            validateErrorResponse(status, error, feignException);
        }
    }
}
