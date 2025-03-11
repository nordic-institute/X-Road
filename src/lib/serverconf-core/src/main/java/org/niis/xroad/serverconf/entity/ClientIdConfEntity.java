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
import ee.ria.xroad.common.util.Validation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import static ee.ria.xroad.common.util.Validation.validateArgument;
import static ee.ria.xroad.common.util.Validation.validateOptionalArgument;
import static org.niis.xroad.serverconf.entity.ClientIdConfEntity.DISCRIMINATOR_VALUE;

@Getter
@Setter
@Entity
@DiscriminatorValue(DISCRIMINATOR_VALUE)
public class ClientIdConfEntity extends XRoadIdConfEntity implements ee.ria.xroad.common.identifier.ClientId {
    public static final String DISCRIMINATOR_VALUE = "C";

    protected ClientIdConfEntity(XRoadObjectType objectType, String xRoadInstance, String memberClass,
                                 String memberCode, String subsystemCode) {
        super(DISCRIMINATOR_VALUE, objectType, xRoadInstance, memberClass);
        setMemberCode(memberCode);
        setSubsystemCode(subsystemCode);
    }

    public ClientIdConfEntity() {
    }

    public static ClientIdConfEntity create(ee.ria.xroad.common.identifier.ClientId identifier) {
        Validation.validateArgument("identifier", identifier);

        return identifier.getSubsystemCode() == null
                ? createMember(identifier.getXRoadInstance(), identifier.getMemberClass(), identifier.getMemberCode())
                : createSubsystem(identifier.getXRoadInstance(), identifier.getMemberClass(), identifier.getMemberCode(),
                        identifier.getSubsystemCode());
    }

    public static ClientIdConfEntity createMember(String xRoadInstance,
                                             String memberClass,
                                             String memberCode) {

        return create(XRoadObjectType.MEMBER, xRoadInstance, memberClass, memberCode, null);
    }

    public static ClientIdConfEntity createSubsystem(String xRoadInstance,
                                                  String memberClass,
                                                  String memberCode,
                                                  String subsystemCode) {
        validateOptionalArgument("subsystemCode", subsystemCode);

        return create(XRoadObjectType.SUBSYSTEM, xRoadInstance, memberClass, memberCode, subsystemCode);
    }

    private static ClientIdConfEntity create(XRoadObjectType objectType,
                                             String xRoadInstance,
                                             String memberClass,
                                             String memberCode,
                                             String subsystemCode) {
        validateArgument("xRoadInstance", xRoadInstance);
        validateArgument("memberClass", memberClass);
        validateArgument("memberCode", memberCode);

        return new ClientIdConfEntity(objectType, xRoadInstance, memberClass, memberCode, subsystemCode);
    }

    @Override
    @JsonIgnore
    public ClientIdConfEntity getMemberId() {
        if (getSubsystemCode() == null) {
            return this;
        } else {
            return createMember(this.getXRoadInstance(),
                    this.getMemberClass(),
                    this.getMemberCode());
        }
    }

    @Override
    @NoCoverage
    public boolean equals(Object obj) {
        return ee.ria.xroad.common.identifier.ClientId.equals(this, obj);
    }

    @Override
    @NoCoverage
    public int hashCode() {
        return ee.ria.xroad.common.identifier.ClientId.hashCode(this);
    }

}
