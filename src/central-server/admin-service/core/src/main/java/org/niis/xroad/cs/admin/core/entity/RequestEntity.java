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


import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.Origin;

@Entity
@Table(name = RequestEntity.TABLE_NAME)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class RequestEntity extends AuditableEntity {

    public static final String TABLE_NAME = "requests";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = TABLE_NAME + "_id_seq")
    @SequenceGenerator(name = TABLE_NAME + "_id_seq", sequenceName = TABLE_NAME + "_id_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false)
    @Getter
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "security_server_id")
    @Getter
    @Setter
    private SecurityServerIdEntity securityServerId;

    @Column(name = "origin")
    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    private Origin origin;

    @Column(name = "comments")
    @Getter
    @Setter
    private String comments;

    protected RequestEntity() {
        //JPA
    }

    protected RequestEntity(Origin origin, ee.ria.xroad.common.identifier.SecurityServerId identifier, String comments) {
        this.origin = origin;
        this.securityServerId = SecurityServerIdEntity.ensure(identifier);
        this.comments = comments;
    }

    /**
     * Get the type tied to the request.
     *
     * @return {@link ManagementRequestType}
     */
    @Transient
    public abstract ManagementRequestType getManagementRequestType();

}
