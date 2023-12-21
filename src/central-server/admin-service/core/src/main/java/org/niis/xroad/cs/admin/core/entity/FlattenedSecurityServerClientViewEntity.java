/*
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
package org.niis.xroad.cs.admin.core.entity;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.niis.xroad.cs.admin.core.entity.converter.ClientIdConverter;
import org.springframework.data.annotation.Immutable;

import java.util.HashSet;
import java.util.Set;

/**
 * Read-only entity representing SecurityServerClient from view flattened_security_server_client
 * type
 */
@Entity
@Immutable
@Getter
@Setter
@NoArgsConstructor
@Table(name = FlattenedSecurityServerClientViewEntity.TABLE_NAME)
public class FlattenedSecurityServerClientViewEntity extends AuditableEntity {
    // Subselect prevents table creation: https://stackoverflow.com/a/33689357
    public static final String TABLE_NAME = "flattened_security_server_client";

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private int id;

    @Column(name = "xroad_instance")
    private String xroadInstance;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_class_id")
    private MemberClassEntity memberClass;

    @Column(name = "member_code")
    private String memberCode;

    @Column(name = "subsystem_code")
    private String subsystemCode;

    @Column(name = "member_name")
    private String memberName;

    @Column(name = "type")
    @Convert(converter = ClientIdConverter.Impl.class)
    private XRoadObjectType type;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "flattenedSecurityServerClientView")
    private Set<FlattenedServerClientEntity> flattenedServerClients = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "server_client_id", nullable = false, updatable = false)
    @Access(AccessType.FIELD)
    @Getter
    private ClientIdEntity identifier;
}


