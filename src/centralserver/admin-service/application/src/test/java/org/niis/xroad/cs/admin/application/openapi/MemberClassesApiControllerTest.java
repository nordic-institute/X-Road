/**
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
package org.niis.xroad.cs.admin.application.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.openapi.model.MemberClassDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MemberClassesApiControllerTest extends AbstractApiControllerTest {
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = {"ADD_MEMBER_CLASS"})
    public void addMemberClass() throws Exception {
        callAddMemberClass();
    }

    @Test
    @WithMockUser(authorities = {"VIEW_MEMBER_CLASSES", "ADD_MEMBER_CLASS", "EDIT_MEMBER_CLASS"})
    public void updateMemberClassDescription() throws Exception {
        callGetMemberClasses(3);
        var memberClassString = callAddMemberClass().andReturn().getResponse().getContentAsString();
        var memberClass = objectMapper.readValue(memberClassString, MemberClassDto.class);
        memberClass.setDescription("New description");

        mockMvc.perform(put(commonModuleEndpointPaths.getBasePath() + "/member-classes/{code}", memberClass.getCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memberClass)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code", equalTo(memberClass.getCode())))
                .andExpect(jsonPath("description", equalTo(memberClass.getDescription())));

        callGetMemberClasses(4);
    }

    @Test
    @WithMockUser(authorities = {"VIEW_MEMBER_CLASSES", "ADD_MEMBER_CLASS", "DELETE_MEMBER_CLASS"})
    public void deleteMemberClass() throws Exception {
        var memberClassString = callAddMemberClass().andReturn().getResponse().getContentAsString();
        var memberClass = objectMapper.readValue(memberClassString, MemberClassDto.class);
        callGetMemberClasses(4);

        mockMvc.perform(delete(commonModuleEndpointPaths.getBasePath() + "/member-classes/{code}", memberClass.getCode()))
                .andExpect(status().isOk());

        callGetMemberClasses(3);
    }

    @Test
    @WithMockUser(authorities = {"VIEW_MEMBER_CLASSES"})
    public void getMemberClasses() throws Exception {
        callGetMemberClasses(3);
    }

    private void callGetMemberClasses(int resultCount) throws Exception {
        mockMvc.perform(get(commonModuleEndpointPaths.getBasePath() + "/member-classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", equalTo(resultCount)));
    }

    private ResultActions callAddMemberClass() throws Exception {
        var memberClass = new MemberClassDto();
        memberClass.setCode("FOO");
        memberClass.setDescription("Description");

        return mockMvc.perform(post(commonModuleEndpointPaths.getBasePath() + "/member-classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memberClass)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code", equalTo(memberClass.getCode())))
                .andExpect(jsonPath("description", equalTo(memberClass.getDescription())));
    }
}
