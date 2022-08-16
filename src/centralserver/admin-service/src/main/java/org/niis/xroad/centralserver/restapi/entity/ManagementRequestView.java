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
package org.niis.xroad.centralserver.restapi.entity;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Subselect;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestType;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.springframework.data.annotation.Immutable;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import java.time.Instant;

@Entity
@Immutable
@Access(AccessType.FIELD)
// Subselect prevents table creation: https://stackoverflow.com/a/33689357
@Subselect("select * from " + ManagementRequestView.TABLE_NAME)
@Table(name = ManagementRequestView.TABLE_NAME)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagementRequestView {
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
    private Long securityServerIdentifierId;

    @Column(name = "request_processing_id")
    private Long requestProcessingId;

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

    @Column(name = "created_at")
    private Instant createdAt;

    @Transient
    public ManagementRequestType getManagementRequestType() {
        return ManagementRequestType.ofDiscriminatorValue(type);
    }

    @Transient
    public SecurityServerId getSecurityServerId() {
        return SecurityServerId.Conf.create(xroadInstance, memberClass, memberCode, serverCode);
    }
}
