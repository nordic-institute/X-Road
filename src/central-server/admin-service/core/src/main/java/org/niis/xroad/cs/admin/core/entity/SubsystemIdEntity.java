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

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.NoArgsConstructor;

import java.util.Optional;

import static ee.ria.xroad.common.util.Validation.validateArgument;
import static org.niis.xroad.cs.admin.core.entity.SubsystemIdEntity.DISCRIMINATOR_VALUE;

@Entity
@NoArgsConstructor
@DiscriminatorValue(DISCRIMINATOR_VALUE)
public class SubsystemIdEntity extends ClientIdEntity {

    public static final String DISCRIMINATOR_VALUE = "SUBSYSTEM";

    protected SubsystemIdEntity(String xRoadInstance, String memberClass, String memberCode, String subsystemCode) {
        super(XRoadObjectType.SUBSYSTEM, xRoadInstance, memberClass, memberCode);
        setSubsystemCode(subsystemCode);
    }

    protected static SubsystemIdEntity create(ee.ria.xroad.common.identifier.ClientId identifier) {
        validateArgument("identifier", identifier);

        return create(identifier.getXRoadInstance(),
                identifier.getMemberClass(),
                identifier.getMemberCode(),
                identifier.getSubsystemCode());
    }

    public static SubsystemIdEntity create(String xRoadInstance,
                                           String memberClass,
                                           String memberCode,
                                           String subsystemCode) {
        validateArgument("xRoadInstance", xRoadInstance);
        validateArgument("memberClass", memberClass);
        validateArgument("memberCode", memberCode);
        validateArgument("subsystemCode", subsystemCode);

        return new SubsystemIdEntity(xRoadInstance, memberClass, memberCode, subsystemCode);
    }

    @Override
    @Transient
    public MemberIdEntity getMemberId() {
        return MemberIdEntity.create(getXRoadInstance(), getMemberClass(), getMemberCode());
    }

    public static SubsystemIdEntity ensure(ee.ria.xroad.common.identifier.ClientId identifier) {
        return Optional.of(validateArgument("identifier", identifier))
                .filter(SubsystemIdEntity.class::isInstance)
                .map(SubsystemIdEntity.class::cast)
                .orElseGet(() -> SubsystemIdEntity.create(identifier));
    }

}
