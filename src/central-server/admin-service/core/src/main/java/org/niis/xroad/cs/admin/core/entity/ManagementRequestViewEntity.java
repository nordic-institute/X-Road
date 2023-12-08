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
package org.niis.xroad.cs.admin.core.entity;

import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.core.entity.converter.XRoadObjectTypeConverter;
import org.springframework.data.annotation.Immutable;

import java.time.Instant;
import java.util.Map;

import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.ADDRESS_CHANGE_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.AUTH_CERT_DELETION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_DELETION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_DISABLE_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_ENABLE_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_REGISTRATION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.OWNER_CHANGE_REQUEST;

@Entity
@Immutable
@Access(AccessType.FIELD)
@Table(name = ManagementRequestViewEntity.TABLE_NAME)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagementRequestViewEntity {
    static final String TABLE_NAME = "management_request_view";

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private int id;

    @Column(name = "origin")
    @Enumerated(EnumType.STRING)
    private Origin origin;

    @Column(name = "comments")
    private String comments;

    @Column(name = "type")
    private String type;

    @Column(name = "security_server_id")
    private Integer securityServerIdentifierId;

    @Column(name = "request_processing_id")
    private Integer requestProcessingId;

    @Column(name = "request_processing_status")
    @Enumerated(EnumType.STRING)
    private ManagementRequestStatus requestProcessingStatus;

    @Column(name = "security_server_owner_name")
    private String securityServerOwnerName;

    @Column(name = "xroad_instance")
    private String xroadInstance;

    @Column(name = "member_code")
    private String memberCode;

    @Column(name = "member_class")
    private String memberClass;

    @Column(name = "server_code")
    private String serverCode;

    @Column(name = "client_owner_name")
    private String clientOwnerName;

    @Column(name = "client_type")
    @Convert(converter = XRoadObjectTypeConverter.Impl.class)
    private XRoadObjectType clientType;

    @Column(name = "client_xroad_instance")
    private String clientXroadInstance;

    @Column(name = "client_member_code")
    private String clientMemberCode;

    @Column(name = "client_member_class")
    private String clientMemberClass;

    @Column(name = "client_subsystem_code")
    private String clientSubsystemCode;

    @Column(name = "auth_cert")
    private byte[] authCert;

    @Column(name = "address")
    private String address;

    @Column(name = "created_at")
    private Instant createdAt;

    @Transient
    public ManagementRequestType getManagementRequestType() {
        return ManagementRequestTypeDiscriminatorMapping.getManagementRequestType(type);
    }

    @Transient
    public SecurityServerId getSecurityServerId() {
        return SecurityServerId.Conf.create(xroadInstance, memberClass, memberCode, serverCode);
    }

    public static class ManagementRequestTypeDiscriminatorMapping {
        private static final Map<ManagementRequestType, String> MAPPING = Map.of(
                AUTH_CERT_REGISTRATION_REQUEST, AuthenticationCertificateRegistrationRequestEntity.DISCRIMINATOR_VALUE,
                CLIENT_REGISTRATION_REQUEST, ClientRegistrationRequestEntity.DISCRIMINATOR_VALUE,
                OWNER_CHANGE_REQUEST, OwnerChangeRequestEntity.DISCRIMINATOR_VALUE,
                CLIENT_DELETION_REQUEST, ClientDeletionRequestEntity.DISCRIMINATOR_VALUE,
                CLIENT_DISABLE_REQUEST, ClientDisableRequestEntity.DISCRIMINATOR_VALUE,
                CLIENT_ENABLE_REQUEST, ClientEnableRequestEntity.DISCRIMINATOR_VALUE,
                AUTH_CERT_DELETION_REQUEST, AuthenticationCertificateDeletionRequestEntity.DISCRIMINATOR_VALUE,
                ADDRESS_CHANGE_REQUEST, AddressChangeRequestEntity.DISCRIMINATOR_VALUE
        );

        public static ManagementRequestType getManagementRequestType(String discriminator) {
            return MAPPING.entrySet().stream()
                    .filter(entry -> entry.getValue().equalsIgnoreCase(discriminator))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(null);
        }

        public static String getDiscriminator(ManagementRequestType managementRequestType) {
            return MAPPING.get(managementRequestType);
        }
    }

}
