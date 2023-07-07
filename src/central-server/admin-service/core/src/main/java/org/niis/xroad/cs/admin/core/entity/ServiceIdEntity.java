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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.NoCoverage;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import static ee.ria.xroad.common.util.Validation.validateArgument;
import static org.niis.xroad.cs.admin.core.entity.ServiceIdEntity.DISCRIMINATOR_VALUE;

@Entity
@DiscriminatorValue(DISCRIMINATOR_VALUE)
public class ServiceIdEntity extends XRoadIdEntity implements ee.ria.xroad.common.identifier.ServiceId {

    public static final String DISCRIMINATOR_VALUE = "SERVICE";

    public static ServiceIdEntity create(ClientId client,
                                         String serviceCode) {
        return create(client.getXRoadInstance(), client.getMemberClass(),
                client.getMemberCode(), client.getSubsystemCode(), serviceCode);
    }

    public static ServiceIdEntity create(ClientId client,
                                         String serviceCode,
                                         String serviceVersion) {
        return create(client.getXRoadInstance(), client.getMemberClass(),
                client.getMemberCode(), client.getSubsystemCode(), serviceCode,
                serviceVersion);
    }

    public static ServiceIdEntity create(String xRoadInstance,
                                         String memberClass,
                                         String memberCode,
                                         String subsystemCode,
                                         String serviceCode) {
        return create(xRoadInstance, memberClass, memberCode, subsystemCode,
                serviceCode, null);
    }

    public static ServiceIdEntity create(String xRoadInstance,
                                         String memberClass,
                                         String memberCode,
                                         String subsystemCode,
                                         String serviceCode,
                                         String serviceVersion) {
        validateArgument("xRoadInstance", xRoadInstance);
        validateArgument("memberClass", memberClass);
        validateArgument("memberCode", memberCode);
        validateArgument("serviceCode", serviceCode);
        return new ServiceIdEntity(XRoadObjectType.SERVICE, xRoadInstance, memberClass,
                memberCode, subsystemCode, serviceCode, serviceVersion);
    }

    public static ServiceIdEntity create(ServiceId identifier) {
        validateArgument("identifier", identifier);

        return create(identifier.getXRoadInstance(),
                identifier.getMemberClass(),
                identifier.getMemberCode(),
                identifier.getSubsystemCode(),
                identifier.getServiceCode(),
                identifier.getServiceVersion());
    }

    protected ServiceIdEntity() {
        // JPA
    }

    protected ServiceIdEntity(XRoadObjectType type,
                              String xRoadInstance,
                              String memberClass,
                              String memberCode,
                              String subsystemCode,
                              String serviceCode,
                              String serviceVersion) {
        super(type, xRoadInstance, memberClass);
        setMemberCode(memberCode);
        setSubsystemCode(subsystemCode);
        setServiceCode(serviceCode);
        setServiceVersion(serviceVersion);
    }

    @JsonIgnore
    @Transient
    public ClientIdEntity getClientId() {
        return SubsystemIdEntity.create(getXRoadInstance(), getMemberClass(), getMemberCode(), getSubsystemCode());
    }

    @Override
    @NoCoverage
    public boolean equals(Object obj) {
        return ee.ria.xroad.common.identifier.ServiceId.equals(this, obj);
    }

    @Override
    @NoCoverage
    public int hashCode() {
        return ee.ria.xroad.common.identifier.ServiceId.hashCode(this);
    }

}
