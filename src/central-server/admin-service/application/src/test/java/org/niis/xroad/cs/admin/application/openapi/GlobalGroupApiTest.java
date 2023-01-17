/**
 * The MIT License
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
package org.niis.xroad.cs.admin.application.openapi;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.application.util.TestUtils;
import org.niis.xroad.cs.openapi.model.GlobalGroupCodeAndDescriptionDto;
import org.niis.xroad.cs.openapi.model.GlobalGroupResourceDto;
import org.niis.xroad.cs.openapi.model.GroupMembersFilterDto;
import org.niis.xroad.cs.openapi.model.GroupMembersFilterModelDto;
import org.niis.xroad.cs.openapi.model.PagedGroupMemberDto;
import org.niis.xroad.cs.openapi.model.PagingSortingParametersDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.cs.openapi.model.ClientTypeDto.MEMBER;
import static org.niis.xroad.cs.openapi.model.ClientTypeDto.SUBSYSTEM;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

@Transactional
class GlobalGroupApiTest extends AbstractApiControllerTestContext {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void addGlobalGroup() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        var entity = prepareAddGlobalGroupRequest("code");

        ResponseEntity<GlobalGroupResourceDto> response =
                restTemplate.postForEntity("/api/v1/global-groups", entity, GlobalGroupResourceDto.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertAddedGlobalGroup(response.getBody());
    }

    @Test
    void addGlobalGroupWhenGlobalGroupExists() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        var entity = prepareAddGlobalGroupRequest("CODE_1");

        ResponseEntity<GlobalGroupResourceDto> response =
                restTemplate.postForEntity("/api/v1/global-groups", entity, GlobalGroupResourceDto.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void findGlobalGroups() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<GlobalGroupResourceDto[]> response = restTemplate.getForEntity(
                "/api/v1/global-groups",
                GlobalGroupResourceDto[].class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(Objects.requireNonNull(response.getBody()).length).isGreaterThanOrEqualTo(1);
        GlobalGroupResourceDto expectedGroup = Arrays.stream(response.getBody())
                .filter(ent -> 1000001 == ent.getId())
                .findFirst()
                .orElse(null);
        assertThat(expectedGroup).isNotNull();
        assertGlobalGroup(expectedGroup);
    }

    @Test
    void getGlobalGroups() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<GlobalGroupResourceDto> response = restTemplate.getForEntity(
                "/api/v1/global-groups/1000001",
                GlobalGroupResourceDto.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertGlobalGroup(response.getBody());
    }

    @Test
    void findGlobalGroupMembers() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        var filter = new GroupMembersFilterDto();
        filter.setMemberClass("GOV");
        filter.setInstance("TEST");
        filter.setCodes(List.of("M1"));
        filter.setSubsystems(List.of("SS1"));
        filter.setTypes(List.of(MEMBER, SUBSYSTEM));
        filter.setPagingSorting(new PagingSortingParametersDto());
        ResponseEntity<PagedGroupMemberDto> response = restTemplate.postForEntity(
                "/api/v1/global-groups/1000001/members",
                filter,
                PagedGroupMemberDto.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertPagedGroupMember(response.getBody());
    }

    @Test
    void findGlobalGroupMembersByQuery() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        var filter = new GroupMembersFilterDto();
        filter.setQuery("gov");
        filter.setPagingSorting(new PagingSortingParametersDto());
        ResponseEntity<PagedGroupMemberDto> response = restTemplate.postForEntity(
                "/api/v1/global-groups/1000001/members",
                filter,
                PagedGroupMemberDto.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertPagedGroupMember(response.getBody());
    }

    @Test
    void findGlobalGroupMembersWithEmptyFilter() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        var filter = new GroupMembersFilterDto();
        filter.setPagingSorting(new PagingSortingParametersDto());
        ResponseEntity<PagedGroupMemberDto> response = restTemplate.postForEntity(
                "/api/v1/global-groups/1000001/members",
                filter,
                PagedGroupMemberDto.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertPagedGroupMember(response.getBody());
    }

    @Test
    void getGroupMembersFilterModel() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<GroupMembersFilterModelDto> response = restTemplate.getForEntity(
                "/api/v1/global-groups/1000001/members/filter-model",
                GroupMembersFilterModelDto.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertGroupMembersFilterModel(response.getBody());
    }

    @Test
    void getGroupMembersFilterModelWhenMembersNotExists() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<GroupMembersFilterModelDto> response = restTemplate.getForEntity(
                "/api/v1/global-groups/1000009/members/filter-model",
                GroupMembersFilterModelDto.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertEmptyGroupMembersFilterModel(response.getBody());
    }

    @Test
    void deleteGlobalGroup() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<GlobalGroupResourceDto> existingGlobalGroup =
                restTemplate.getForEntity("/api/v1/global-groups/1000003", GlobalGroupResourceDto.class);
        assertThat(existingGlobalGroup.getBody()).isNotNull();
        assertThat(existingGlobalGroup.getStatusCode()).isEqualTo(OK);
        assertThat(existingGlobalGroup.getBody().getId()).isEqualTo(1000003);

        restTemplate.delete("/api/v1/global-groups/1000003");

        ResponseEntity<GlobalGroupResourceDto> deleteGlobalGroup =
                restTemplate.getForEntity("/api/v1/global-groups/1000003", GlobalGroupResourceDto.class);

        assertThat(deleteGlobalGroup.getBody()).isNotNull();
        assertThat(deleteGlobalGroup.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void updateGlobalGroupDescription() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        GlobalGroupResourceDto updatedGlobalGroup = restTemplate.patchForObject("/api/v1/global-groups/1000002",
                Collections.singletonMap("description", "New description"), GlobalGroupResourceDto.class);
        assertThat(updatedGlobalGroup.getDescription()).isEqualTo("New description");
    }

    private HttpEntity<GlobalGroupCodeAndDescriptionDto> prepareAddGlobalGroupRequest(String code) {
        GlobalGroupCodeAndDescriptionDto request = new GlobalGroupCodeAndDescriptionDto();
        request.code(code);
        request.description("description");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(request, headers);
    }

    private void assertPagedGroupMember(PagedGroupMemberDto pagedGroupMember) {
        assertThat(pagedGroupMember.getItems().size()).isEqualTo(1);
        var member = pagedGroupMember.getItems().get(0);
        assertThat(member.getId()).isEqualTo("1000001");
        assertThat(member.getCode()).isEqualTo("M1");
        assertThat(member.getName()).isEqualTo("TEST:GOV:M1:SS1");
        assertThat(member.getInstance()).isEqualTo("TEST");
        assertThat(member.getSubsystem()).isEqualTo("SS1");
        assertThat(member.getPropertyClass()).isEqualTo("GOV");
        assertThat(member.getType()).isEqualTo("SUBSYSTEM");
        var pagingMetadata = pagedGroupMember.getPagingMetadata();
        assertThat(pagingMetadata.getItems()).isEqualTo(1);
        assertThat(pagingMetadata.getOffset()).isZero();
        assertThat(pagingMetadata.getLimit()).isEqualTo(25);
        assertThat(pagingMetadata.getTotalItems()).isEqualTo(1);
    }

    private void assertGlobalGroup(GlobalGroupResourceDto globalGroup) {
        assertThat(globalGroup.getId()).isEqualTo(1000001);
        assertThat(globalGroup.getCode()).isEqualTo("CODE_1");
        assertThat(globalGroup.getDescription()).isEqualTo("First global group");
        assertThat(globalGroup.getMemberCount()).isEqualTo(1);
        assertThat(globalGroup.getCreatedAt()).isNotNull();
        assertThat(globalGroup.getUpdatedAt()).isNotNull();
    }

    private void assertGroupMembersFilterModel(GroupMembersFilterModelDto filterModel) {
        assertThat(filterModel).isNotNull();
        assertThat(filterModel.getInstances().size()).isEqualTo(1);
        assertThat(filterModel.getInstances().get(0)).isEqualTo("TEST");
        assertThat(filterModel.getMemberClasses().size()).isEqualTo(1);
        assertThat(filterModel.getMemberClasses().get(0)).isEqualTo("GOV");
        assertThat(filterModel.getCodes().size()).isEqualTo(1);
        assertThat(filterModel.getCodes().get(0)).isEqualTo("M1");
        assertThat(filterModel.getSubsystems().size()).isEqualTo(1);
        assertThat(filterModel.getSubsystems().get(0)).isEqualTo("SS1");
    }

    private void assertEmptyGroupMembersFilterModel(GroupMembersFilterModelDto filterModel) {
        assertThat(filterModel).isNotNull();
        assertThat(filterModel.getInstances().size()).isZero();
        assertThat(filterModel.getMemberClasses().size()).isZero();
        assertThat(filterModel.getCodes().size()).isZero();
        assertThat(filterModel.getSubsystems().size()).isZero();
    }

    private void assertAddedGlobalGroup(GlobalGroupResourceDto globalGroup) {
        assertThat(globalGroup.getId()).isNotNull();
        assertThat(globalGroup.getCode()).isEqualTo("code");
        assertThat(globalGroup.getDescription()).isEqualTo("description");
        assertThat(globalGroup.getMemberCount()).isZero();
        assertThat(globalGroup.getCreatedAt()).isNotNull();
        assertThat(globalGroup.getUpdatedAt()).isNotNull();
    }
}
