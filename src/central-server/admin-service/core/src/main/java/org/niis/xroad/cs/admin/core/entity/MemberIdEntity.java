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
import ee.ria.xroad.common.util.Validation;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import java.util.Optional;

import static org.niis.xroad.cs.admin.core.entity.MemberIdEntity.DISCRIMINATOR_VALUE;

@Entity
@DiscriminatorValue(DISCRIMINATOR_VALUE)
public class MemberIdEntity extends ClientIdEntity {

    public static final String DISCRIMINATOR_VALUE = "MEMBER";

    protected MemberIdEntity() {
        //for JPA
    }

    protected MemberIdEntity(String xRoadInstance, String memberClass, String memberCode) {
        super(XRoadObjectType.MEMBER, xRoadInstance, memberClass, memberCode);
    }

    public static MemberIdEntity create(ee.ria.xroad.common.identifier.ClientId identifier) {
        Validation.validateArgument("identifier", identifier);

        return create(identifier.getXRoadInstance(),
                identifier.getMemberClass(),
                identifier.getMemberCode());
    }

    public static MemberIdEntity create(String xRoadInstance, String memberClass, String memberCode) {
        Validation.validateArgument("xRoadInstance", xRoadInstance);
        Validation.validateArgument("memberClass", memberClass);
        Validation.validateArgument("memberCode", memberCode);

        return new MemberIdEntity(xRoadInstance, memberClass, memberCode);
    }

    @Override
    public MemberIdEntity getMemberId() {
        return this;
    }

    public static MemberIdEntity ensure(ee.ria.xroad.common.identifier.ClientId identifier) {
        return Optional.of(Validation.validateArgument("identifier", identifier))
                .filter(MemberIdEntity.class::isInstance)
                .map(MemberIdEntity.class::cast)
                .orElseGet(() -> MemberIdEntity.create(identifier));
    }

}
