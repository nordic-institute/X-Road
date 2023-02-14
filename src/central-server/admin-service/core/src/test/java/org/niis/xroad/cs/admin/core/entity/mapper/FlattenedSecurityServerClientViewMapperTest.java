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
package org.niis.xroad.cs.admin.core.entity.mapper;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.core.entity.FlattenedSecurityServerClientViewEntity;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {FlattenedSecurityServerClientViewMapperImpl.class})
class FlattenedSecurityServerClientViewMapperTest {
    @Autowired
    private FlattenedSecurityServerClientViewMapper mapper;

    @Test
    void shouldMapAllFields() {
        var source = new FlattenedSecurityServerClientViewEntity();
        source.setId(100);
        source.setXroadInstance("xroadInstance");
        source.setMemberClass(new MemberClassEntity("memberCode", "desc"));
        source.setMemberCode("memberCode");
        source.setSubsystemCode("subsystemCode");
        source.setMemberName("memberName");
        source.setType(XRoadObjectType.SERVER);

        var result = mapper.toTarget(source);
        assertThat(result.getId()).isEqualTo(source.getId());
        assertThat(result.getXroadInstance()).isEqualTo(source.getXroadInstance());
        assertThat(result.getMemberClass().getCode()).isEqualTo(source.getMemberClass().getCode());
        assertThat(result.getMemberCode()).isEqualTo(source.getMemberCode());
        assertThat(result.getSubsystemCode()).isEqualTo(source.getSubsystemCode());
        assertThat(result.getMemberName()).isEqualTo(source.getMemberName());
        assertThat(result.getType()).isEqualTo(source.getType());
    }
}
