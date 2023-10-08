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
package org.niis.xroad.cs.admin.api.domain;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.NoCoverage;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Optional;

import static ee.ria.xroad.common.util.Validation.validateArgument;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class SecurityServerId extends XRoadId implements ee.ria.xroad.common.identifier.SecurityServerId {

    protected SecurityServerId(XRoadObjectType type,
                               String xRoadInstance,
                               String memberClass,
                               String memberCode,
                               String serverCode) {
        super(type, xRoadInstance, memberClass);
        setMemberCode(memberCode);
        setServerCode(serverCode);
    }

    @Override
    public MemberId getOwner() {
        return new MemberId(getXRoadInstance(), getMemberClass(), getMemberCode());
    }

    @Override
    @NoCoverage
    public boolean equals(Object obj) {
        return ee.ria.xroad.common.identifier.SecurityServerId.equals(this, obj);
    }

    @Override
    @NoCoverage
    public int hashCode() {
        return ee.ria.xroad.common.identifier.SecurityServerId.hashCode(this);
    }

    public static SecurityServerId create(ClientId identifier, String serverCode) {
        validateArgument("identifier", identifier);

        return create(identifier.getXRoadInstance(),
                identifier.getMemberClass(),
                identifier.getMemberCode(),
                serverCode);
    }

    public static SecurityServerId create(ee.ria.xroad.common.identifier.SecurityServerId identifier) {
        validateArgument("identifier", identifier);

        return create(identifier.getXRoadInstance(),
                identifier.getMemberClass(),
                identifier.getMemberCode(),
                identifier.getServerCode());
    }

    public static SecurityServerId create(String xRoadInstance,
                                          String memberClass,
                                          String memberCode,
                                          String serverCode) {
        validateArgument("xRoadInstance", xRoadInstance);
        validateArgument("memberClass", memberClass);
        validateArgument("memberCode", memberCode);
        validateArgument("serverCode", serverCode);

        return new SecurityServerId(XRoadObjectType.SERVER, xRoadInstance, memberClass, memberCode, serverCode);
    }

    public static SecurityServerId ensure(
            ee.ria.xroad.common.identifier.SecurityServerId identifier) {
        return Optional.ofNullable(identifier)
                .filter(SecurityServerId.class::isInstance)
                .map(SecurityServerId.class::cast)
                .orElseGet(() -> SecurityServerId.create(identifier));
    }

}
