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
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(classes = {SecurityServerMapperImpl.class, SecurityServerClientMapperImpl.class, ClientIdMapperImpl.class,
        ServerClientMapperImpl.class})
class SecurityServerMapperTest {

    private static final String SERVER_CODE = "server-code";
    private static final String ADDRESS = "ss.example.org";
    private static final String SYSTEM_DID = "did:example:system";
    private static final String DSP_BASE_URL = "https://ss.example.org:8443/dsp";

    @Autowired
    private SecurityServerMapper securityServerMapper;

    @Test
    void shouldMapPublishedValuesWhenSet() {
        SecurityServerEntity entity = createEntity();
        entity.setSystemDid(SYSTEM_DID);
        entity.setDspBaseUrl(DSP_BASE_URL);

        SecurityServer server = securityServerMapper.toTarget(entity);

        assertThat(server.getServerCode()).isEqualTo(SERVER_CODE);
        assertThat(server.getAddress()).isEqualTo(ADDRESS);
        assertThat(server.getSystemDid()).isEqualTo(SYSTEM_DID);
        assertThat(server.getDspBaseUrl()).isEqualTo(DSP_BASE_URL);
    }

    @Test
    void shouldMapPublishedValuesWhenEmpty() {
        SecurityServer server = securityServerMapper.toTarget(createEntity());

        assertThat(server.getServerCode()).isEqualTo(SERVER_CODE);
        assertThat(server.getSystemDid()).isNull();
        assertThat(server.getDspBaseUrl()).isNull();
    }

    private SecurityServerEntity createEntity() {
        var memberClass = new MemberClassEntity("member-class", "class-description");
        var memberId = MemberIdEntity.create("instance", "member-class", "member-code");
        var owner = new XRoadMemberEntity("member-name", memberId, memberClass);
        var entity = new SecurityServerEntity(owner, SERVER_CODE);
        entity.setAddress(ADDRESS);
        return entity;
    }
}
