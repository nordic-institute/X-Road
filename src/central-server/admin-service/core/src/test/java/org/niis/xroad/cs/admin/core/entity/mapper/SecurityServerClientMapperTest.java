/*
 * The MIT License
 *
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

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.ServerClient;
import org.niis.xroad.cs.admin.api.domain.Subsystem;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemIdEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static ee.ria.xroad.common.identifier.XRoadObjectType.SERVER;
import static ee.ria.xroad.common.identifier.XRoadObjectType.SUBSYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {SecurityServerClientMapperImpl.class, ClientIdMapperImpl.class, ServerClientMapperImpl.class})
class SecurityServerClientMapperTest {

    private static final String MEMBER_CLASS = "member-class";
    private static final String MEMBER_CODE = "member-code";
    private static final String MEMBER_NAME = "member-name";
    private static final String SUBSYSTEM_CODE = "subsystem";
    private static final String INSTANCE = "instance";
    private static final String SERVER_CODE = "server-code";
    private static final String OWNER_NAME = "owner-name";
    private static final String CLASS_DESCRIPTION = "class-description";
    private static final int SUBSYSTEM_ID = 123;

    @Autowired
    private SecurityServerClientMapper securityServerClientMapper;

    @Test
    void toDto() {

        SubsystemEntity entity = createSubsystemEntity();

        final Subsystem subsystem = (Subsystem) securityServerClientMapper.toTarget(entity);

        assertThat(subsystem.getId()).isEqualTo(SUBSYSTEM_ID);
        assertThat(subsystem.getSubsystemCode()).isEqualTo(SUBSYSTEM_CODE);
        assertThat(subsystem.getCreatedAt()).isNotNull();
        assertThat(subsystem.getUpdatedAt()).isNotNull();

        final org.niis.xroad.cs.admin.api.domain.ClientId identifier = subsystem.getIdentifier();
        assertThat(identifier.getXRoadInstance()).isEqualTo(INSTANCE);
        assertThat(identifier.getMemberClass()).isEqualTo(MEMBER_CLASS);
        assertThat(identifier.getMemberCode()).isEqualTo(MEMBER_CODE);
        assertThat(identifier.getSubsystemCode()).isEqualTo(SUBSYSTEM_CODE);
        assertThat(identifier.getObjectType()).isEqualTo(SUBSYSTEM);
        assertThat(identifier.getCreatedAt()).isNotNull();
        assertThat(identifier.getUpdatedAt()).isNotNull();

        assertThat(subsystem.getXroadMember()).isNotNull();
        assertThat(subsystem.getXroadMember().getMemberCode()).isEqualTo(MEMBER_CODE);
        assertThat(subsystem.getXroadMember().getName()).isEqualTo(MEMBER_NAME);
        assertThat(subsystem.getXroadMember().getMemberClass().getCode()).isEqualTo(MEMBER_CLASS);
        assertThat(subsystem.getXroadMember().getMemberClass().getDescription()).isEqualTo(CLASS_DESCRIPTION);


        assertThat(subsystem.getServerClients()).hasSize(1);
        final ServerClient serverClient = subsystem.getServerClients().iterator().next();

        assertThat(serverClient.getServerOwner()).isEqualTo(OWNER_NAME);
        assertThat(serverClient.getServerCode()).isEqualTo(SERVER_CODE);

        final SecurityServerId serverId = serverClient.getServerId();
        assertThat(serverId).isNotNull();
        assertThat(serverId.getObjectType()).isEqualTo(SERVER);
        assertThat(serverId.getXRoadInstance()).isEqualTo(INSTANCE);
        assertThat(serverId.getMemberClass()).isEqualTo(MEMBER_CLASS);
        assertThat(serverId.getMemberCode()).isEqualTo(MEMBER_CODE);
        assertThat(serverId.getServerCode()).isEqualTo(SERVER_CODE);
    }

    private SubsystemEntity createSubsystemEntity() {
        final var memberClasEntity = new MemberClassEntity(MEMBER_CLASS, CLASS_DESCRIPTION);
        final var memberIdEntity = MemberIdEntity.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE);

        final XRoadMemberEntity member = new XRoadMemberEntity(MEMBER_NAME, memberIdEntity, memberClasEntity);

        final ClientId identifier = SubsystemIdEntity.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE, SUBSYSTEM_CODE);
        final SubsystemEntity entity = new SubsystemEntity(member, identifier);
        ReflectionTestUtils.setField(entity, "id", 2);
        final ServerClientEntity serverClient = new ServerClientEntity();
        final XRoadMemberEntity owner = new XRoadMemberEntity(OWNER_NAME, memberIdEntity, memberClasEntity);
        serverClient.setSecurityServer(new SecurityServerEntity(owner, SERVER_CODE));
        serverClient.setSecurityServerClient(entity);
        entity.getServerClients().add(serverClient);
        ReflectionTestUtils.setField(entity, "id", SUBSYSTEM_ID);
        return entity;
    }

}
