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
package org.niis.xroad.cs.admin.core.entity;

import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.NoCoverage;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.cs.admin.core.entity.converter.XRoadObjectTypeConverter;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = XRoadIdEntity.TABLE_NAME)
@DiscriminatorColumn(name = "object_type", discriminatorType = DiscriminatorType.STRING)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class XRoadIdEntity extends AuditableEntity
        implements ee.ria.xroad.common.identifier.XRoadId {

    public static final String TABLE_NAME = "identifiers";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = TABLE_NAME + "_id_seq")
    @SequenceGenerator(name = TABLE_NAME + "_id_seq", sequenceName = TABLE_NAME + "_id_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false)
    @Access(AccessType.FIELD)
    @Getter
    private int id;

    @Column(name = "object_type", insertable = false, updatable = false)
    @Convert(converter = XRoadObjectTypeConverter.Impl.class)
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private XRoadObjectType objectType;

    @Column(name = "xroad_instance")
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private String xRoadInstance;

    @Column(name = "member_class")
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private String memberClass;

    @Column(name = "member_code")
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private String memberCode;

    @Column(name = "subsystem_code")
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private String subsystemCode;

    @Column(name = "service_code")
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private String serviceCode;

    @Column(name = "server_code")
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private String serverCode;

    @Column(name = "service_version")
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private String serviceVersion;

    protected XRoadIdEntity() {
        //JPA
    }

    protected XRoadIdEntity(XRoadObjectType objectType, String xRoadInstance, String memberClass) {
        this.objectType = objectType;
        this.xRoadInstance = xRoadInstance;
        this.memberClass = memberClass;
    }

    @Override
    @NoCoverage
    public String toString() {
        return ee.ria.xroad.common.identifier.XRoadId.toString(this);
    }

    @Override
    @NoCoverage
    public boolean equals(Object obj) {
        return ee.ria.xroad.common.identifier.XRoadId.equals(this, obj);
    }

    @Override
    @NoCoverage
    public int hashCode() {
        return ee.ria.xroad.common.identifier.XRoadId.hashCode(this);
    }

}

