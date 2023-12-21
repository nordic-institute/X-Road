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
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.AddressChangeRequest;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.ClientRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.OwnerChangeRequest;
import org.niis.xroad.cs.admin.core.entity.AddressChangeRequestEntity;
import org.niis.xroad.cs.admin.core.entity.AuthenticationCertificateDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.AuthenticationCertificateRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.OwnerChangeRequestEntity;
import org.niis.xroad.cs.admin.core.entity.RequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {RequestMapperImpl.class, ClientIdMapperImpl.class, SecurityServerIdMapperImpl.class})
class RequestMapperTest {
    private static final byte[] CERT = {1, 0, 1};

    private static final MemberIdEntity MEMBER_ID_ENTITY = MemberIdEntity.create(
            "xRoadInstance", "memberClass", "memberCode");

    private static final SecurityServerIdEntity SECURITY_SERVER_ID_ENTITY = SecurityServerIdEntity.create(
            "xRoadInstance", "memberClass", "memberCode", "serverCode");

    @Autowired
    private RequestMapper mapper;

    @Test
    void shouldMapAuthenticationCertificateDeletionRequestEntity() {
        var source = new AuthenticationCertificateDeletionRequestEntity(Origin.SECURITY_SERVER, SECURITY_SERVER_ID_ENTITY,
                CERT, "comments");

        var result = mapper.toTarget(source);

        assertThat(result).isInstanceOf(AuthenticationCertificateDeletionRequest.class);
        assertThat(result.getOrigin()).isEqualTo(source.getOrigin());
        assertThat(result.getComments()).isEqualTo(source.getComments());
        assertThat(((AuthenticationCertificateDeletionRequest) result).getAuthCert()).isEqualTo(source.getAuthCert());
        assertThat(result.getManagementRequestType()).isEqualTo(ManagementRequestType.AUTH_CERT_DELETION_REQUEST);
        assertThat(result.getSecurityServerId().getServerCode()).isEqualTo(source.getSecurityServerId().getServerCode());
    }

    @Test
    void shouldMapAuthenticationCertificateRegistrationRequestEntity() {
        var source = new AuthenticationCertificateRegistrationRequestEntity(Origin.SECURITY_SERVER, SECURITY_SERVER_ID_ENTITY, "comments");
        source.setAuthCert(CERT);
        source.setAddress("address");

        var result = mapper.toTarget(source);

        assertThat(result).isExactlyInstanceOf(AuthenticationCertificateRegistrationRequest.class);
        assertThat(result.getOrigin()).isEqualTo(source.getOrigin());
        assertThat(result.getComments()).isEqualTo(source.getComments());
        assertThat(((AuthenticationCertificateRegistrationRequest) result).getAddress()).isEqualTo(source.getAddress());
        assertThat(((AuthenticationCertificateRegistrationRequest) result).getAuthCert()).isEqualTo(source.getAuthCert());
        assertThat(result.getManagementRequestType()).isEqualTo(ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST);
        assertThat(result.getSecurityServerId().getServerCode()).isEqualTo(source.getSecurityServerId().getServerCode());
    }

    @Test
    void shouldMapClientDeletionRequestEntity() {
        var source = new ClientDeletionRequestEntity(Origin.SECURITY_SERVER, SECURITY_SERVER_ID_ENTITY, MEMBER_ID_ENTITY, "comments");

        var result = mapper.toTarget(source);

        assertThat(result).isExactlyInstanceOf(ClientDeletionRequest.class);
        assertThat(result.getOrigin()).isEqualTo(source.getOrigin());
        assertThat(result.getComments()).isEqualTo(source.getComments());
        assertThat(result.getManagementRequestType()).isEqualTo(ManagementRequestType.CLIENT_DELETION_REQUEST);
        assertThat(result.getSecurityServerId().getServerCode()).isEqualTo(source.getSecurityServerId().getServerCode());
        assertThat(((ClientDeletionRequest) result).getClientId().getMemberCode())
                .isEqualTo(source.getClientId().getMemberCode());
    }

    @Test
    void shouldMapClientRegistrationRequestEntity() {
        RequestEntity source = new ClientRegistrationRequestEntity(Origin.SECURITY_SERVER, SECURITY_SERVER_ID_ENTITY,
                MEMBER_ID_ENTITY, "comments");

        var result = mapper.toTarget(source);

        assertThat(result).isExactlyInstanceOf(ClientRegistrationRequest.class);
        assertThat(result.getOrigin()).isEqualTo(source.getOrigin());
        assertThat(result.getComments()).isEqualTo(source.getComments());
        assertThat(result.getManagementRequestType()).isEqualTo(ManagementRequestType.CLIENT_REGISTRATION_REQUEST);
        assertThat(result.getSecurityServerId().getServerCode()).isEqualTo(source.getSecurityServerId().getServerCode());
        assertThat(((ClientRegistrationRequest) result).getClientId().getMemberCode()).isEqualTo(MEMBER_ID_ENTITY.getMemberCode());
    }

    @Test
    void shouldMapOwnerChangeRequestEntity() {
        var source = new OwnerChangeRequestEntity(Origin.SECURITY_SERVER, SECURITY_SERVER_ID_ENTITY, MEMBER_ID_ENTITY, "comments");

        var result = mapper.toTarget(source);

        assertThat(result).isExactlyInstanceOf(OwnerChangeRequest.class);
        assertThat(result.getOrigin()).isEqualTo(source.getOrigin());
        assertThat(result.getComments()).isEqualTo(source.getComments());
        assertThat(result.getManagementRequestType()).isEqualTo(ManagementRequestType.OWNER_CHANGE_REQUEST);
        assertThat(result.getSecurityServerId().getServerCode()).isEqualTo(source.getSecurityServerId().getServerCode());
        assertThat(((OwnerChangeRequest) result).getClientId().getMemberCode())
                .isEqualTo(source.getClientId().getMemberCode());
    }

    @Test
    void shouldMapAddressChangeRequestEntity() {
        var source = new AddressChangeRequestEntity(Origin.SECURITY_SERVER, SECURITY_SERVER_ID_ENTITY,
                "https://server.address");
        source.setComments("comments");

        var result = mapper.toTarget(source);

        assertThat(result).isExactlyInstanceOf(AddressChangeRequest.class);
        assertThat(result.getOrigin()).isEqualTo(source.getOrigin());
        assertThat(result.getComments()).isEqualTo(source.getComments());
        assertThat(result.getManagementRequestType()).isEqualTo(ManagementRequestType.ADDRESS_CHANGE_REQUEST);
        assertThat(result.getSecurityServerId().getServerCode()).isEqualTo(source.getSecurityServerId().getServerCode());
        assertThat(((AddressChangeRequest)result).getServerAddress()).isEqualTo("https://server.address");
    }

}
