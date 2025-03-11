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
package org.niis.xroad.serverconf.entity;

import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.NoCoverage;

import jakarta.persistence.Access;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.serverconf.mapper.XRoadObjectTypeConverter;

import static jakarta.persistence.AccessType.FIELD;

@Getter
@Setter
@Entity
@Table(name = XRoadIdConfEntity.TABLE_NAME)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Access(FIELD)
public abstract class XRoadIdConfEntity implements ee.ria.xroad.common.identifier.XRoadId {

    public static final String TABLE_NAME = "identifier";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "discriminator", nullable = false, insertable = false, updatable = false)
    private String discriminator;

    @Convert(converter = XRoadObjectTypeConverter.Impl.class)
    @Column(name = "type")
    private XRoadObjectType objectType;

    @Column(name = "xroadinstance")
    private String xRoadInstance;

    @Column(name = "memberclass")
    private String memberClass;

    @Column(name = "membercode")
    private String memberCode;

    @Column(name = "subsystemcode")
    private String subsystemCode;

    @Column(name = "serviceversion")
    private String serviceVersion;

    @Column(name = "servicecode")
    private String serviceCode;

    @Column(name = "groupcode")
    private String groupCode;

    @Column(name = "servercode")
    private String serverCode;

    protected XRoadIdConfEntity() {
        //JPA
    }

    protected XRoadIdConfEntity(String discriminator, XRoadObjectType objectType, String xRoadInstance, String memberClass) {
        this.discriminator = discriminator;
        this.objectType = objectType;
        this.xRoadInstance = xRoadInstance;
        this.memberClass = memberClass;
    }

    protected XRoadIdConfEntity(String discriminator,
                                XRoadObjectType objectType,
                                String xRoadInstance,
                                String memberClass,
                                String croupCode) {
        this(discriminator, objectType, xRoadInstance, memberClass);
        this.groupCode = croupCode;
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
