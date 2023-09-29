/*
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
package org.niis.xroad.cs.admin.api.domain;

import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.Validation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
public class MemberId extends ClientId {

    protected MemberId(String xRoadInstance, String memberClass, String memberCode) {
        super(XRoadObjectType.MEMBER, xRoadInstance, memberClass, memberCode);
    }

    public static MemberId create(ee.ria.xroad.common.identifier.ClientId identifier) {
        Validation.validateArgument("identifier", identifier);

        return create(identifier.getXRoadInstance(),
                identifier.getMemberClass(),
                identifier.getMemberCode());
    }

    public static MemberId create(String xRoadInstance, String memberClass, String memberCode) {
        Validation.validateArgument("xRoadInstance", xRoadInstance);
        Validation.validateArgument("memberClass", memberClass);
        Validation.validateArgument("memberCode", memberCode);

        return new MemberId(xRoadInstance, memberClass, memberCode);
    }

    @Override
    public MemberId getMemberId() {
        return this;
    }

    public static MemberId ensure(ee.ria.xroad.common.identifier.ClientId identifier) {
        return Optional.of(Validation.validateArgument("identifier", identifier))
                .filter(MemberId.class::isInstance)
                .map(MemberId.class::cast)
                .orElseGet(() -> MemberId.create(identifier));
    }

}
