/**
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
package org.niis.xroad.centralserver.restapi.openapi;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.MemberClassDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Disabled("Has to be revorked for new architecture.")
public class MemberClassesApiControllerTest extends AbstractApiControllerTestContext {
    @Autowired
    private MemberClassesApiController memberClassesApiController;

    @Test
    @WithMockUser(authorities = {"ADD_MEMBER_CLASS"})
    public void testAddMemberClass() {
        final MemberClassDto mc = new MemberClassDto();
        mc.setCode("FOO");
        mc.setDescription("Description");
        ResponseEntity<MemberClassDto> response = memberClassesApiController.addMemberClass(mc);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(mc.getCode(), response.getBody().getCode());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_MEMBER_CLASSES"})
    public void testGetMemberClass() {
        ResponseEntity<Set<MemberClassDto>> response = memberClassesApiController.getMemberClasses();
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertThat(response.getBody()).hasSize(3);
    }

}
