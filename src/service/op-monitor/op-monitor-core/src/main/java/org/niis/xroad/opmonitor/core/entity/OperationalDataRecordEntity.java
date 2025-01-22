/*
 * The MIT License
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
package org.niis.xroad.opmonitor.core.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

@Setter()
@Getter
@Entity
@Access(AccessType.FIELD)
@Table(name = "operational_data", indexes = {@Index(name = "idx_monitoring_data_ts", columnList = "monitoring_data_ts")})
public class OperationalDataRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "op_data_seq")
    @GenericGenerator(
            name = "op_data_seq",
            strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
            parameters = {
                    @Parameter(name = "optimizer", value = "hilo")
            }
    )
    private Long id;

    @Column(name = "monitoring_data_ts", nullable = false)
    private Long monitoringDataTs;

    @Column(name = "security_server_internal_ip", nullable = false)
    private String securityServerInternalIp;

    @Column(name = "security_server_type", nullable = false)
    private String securityServerType;

    @Column(name = "request_in_ts", nullable = false)
    private Long requestInTs;

    @Column(name = "request_out_ts")
    private Long requestOutTs;

    @Column(name = "response_in_ts")
    private Long responseInTs;

    @Column(name = "response_out_ts", nullable = false)
    private Long responseOutTs;

    @Column(name = "client_xroad_instance")
    private String clientXRoadInstance;

    @Column(name = "client_member_class")
    private String clientMemberClass;

    @Column(name = "client_member_code")
    private String clientMemberCode;

    @Column(name = "client_subsystem_code")
    private String clientSubsystemCode;

    @Column(name = "service_xroad_instance")
    private String serviceXRoadInstance;

    @Column(name = "service_member_class")
    private String serviceMemberClass;

    @Column(name = "service_member_code")
    private String serviceMemberCode;

    @Column(name = "service_subsystem_code")
    private String serviceSubsystemCode;

    @Column(name = "service_code")
    private String serviceCode;

    @Column(name = "rest_method")
    private String restMethod;

    @Column(name = "rest_path")
    private String restPath;

    @Column(name = "service_version")
    private String serviceVersion;

    @Column(name = "represented_party_class")
    private String representedPartyClass;

    @Column(name = "represented_party_code")
    private String representedPartyCode;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "message_user_id")
    private String messageUserId;

    @Column(name = "message_issue")
    private String messageIssue;

    @Column(name = "message_protocol_version")
    private String messageProtocolVersion;

    @Column(name = "client_security_server_address")
    private String clientSecurityServerAddress;

    @Column(name = "service_security_server_address")
    private String serviceSecurityServerAddress;

    @Column(name = "request_size")
    private Long requestSize;

    @Column(name = "request_mime_size")
    private Long requestMimeSize;

    @Column(name = "request_attachment_count")
    private Integer requestAttachmentCount;

    @Column(name = "response_size")
    private Long responseSize;

    @Column(name = "response_mime_size")
    private Long responseMimeSize;

    @Column(name = "response_attachment_count")
    private Integer responseAttachmentCount;

    @Column(name = "succeeded", nullable = false)
    private Boolean succeeded;

    @Column(name = "fault_code")
    private String faultCode;

    @Column(name = "fault_string", length = 2048)
    private String faultString;

    @Column(name = "x_request_id")
    private String xRequestId;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "service_type")
    private String serviceType;

}
