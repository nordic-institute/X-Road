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
package org.niis.xroad.centralserver.restapi.entity;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.Getter;
import org.niis.xroad.centralserver.restapi.entity.converter.ClientIdConverter;
import org.springframework.data.annotation.Immutable;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import java.util.HashSet;
import java.util.Set;

/**
 * Read-only entity representing SecurityServerClient from view flattened_security_server_client
 * type
 */
@Entity
@Immutable
@Table(name = FlattenedSecurityServerClientView.TABLE_NAME)
public class FlattenedSecurityServerClientView extends AuditableEntity {

    public static final String TABLE_NAME = "flattened_security_server_client";

    @Id
    @Column(name = "id", unique = true, nullable = false)
    @Getter
    private int id;

    @Column(name = "xroad_instance")
    @Getter
    private String xRoadInstance;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_class_id")
    @Getter
    private MemberClass memberClass;

    @Column(name = "member_code")
    @Getter
    private String memberCode;

    @Column(name = "subsystem_code")
    @Getter
    private String subsystemCode;

    @Column(name = "member_name")
    @Getter
    private String memberName;

    @Column(name = "type")
    @Convert(converter = ClientIdConverter.Impl.class)
    @Getter
    private XRoadObjectType type;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "flattenedSecurityServerClientView")
    @Getter
    private Set<FlattenedServerClient> flattenedServerClients = new HashSet<>();

    protected FlattenedSecurityServerClientView() {
        //JPA
    }
}


