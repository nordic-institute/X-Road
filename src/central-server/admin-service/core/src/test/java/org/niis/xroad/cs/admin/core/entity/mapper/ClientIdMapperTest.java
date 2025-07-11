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
package org.niis.xroad.cs.admin.core.entity.mapper;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.identifiers.jpa.entity.MemberIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.SubsystemIdEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(classes = {ClientIdMapperImpl.class})
class ClientIdMapperTest {

    @Autowired
    private ClientIdMapper clientIdMapper;

    @Test
    void shouldMapAllMemberFields() {
        var source = MemberIdEntity.create("xRoadInstance", "memberClass",
                "memberCode");

        var result = clientIdMapper.toTarget(source);

        assertThat(result.getXRoadInstance()).isEqualTo(source.getXRoadInstance());
        assertThat(result.getMemberClass()).isEqualTo(source.getMemberClass());
        assertThat(result.getMemberCode()).isEqualTo(source.getMemberCode());
        assertThat(result.getSubsystemCode()).isEqualTo(source.getSubsystemCode());
    }

    @Test
    void shouldMapAllSubsystemFields() {
        var source = SubsystemIdEntity.create("xRoadInstance", "memberClass",
                "memberCode", "subsystemCode");

        var result = clientIdMapper.toTarget(source);

        assertThat(result.getXRoadInstance()).isEqualTo(source.getXRoadInstance());
        assertThat(result.getMemberClass()).isEqualTo(source.getMemberClass());
        assertThat(result.getMemberCode()).isEqualTo(source.getMemberCode());
        assertThat(result.getSubsystemCode()).isEqualTo(source.getSubsystemCode());
    }
}
