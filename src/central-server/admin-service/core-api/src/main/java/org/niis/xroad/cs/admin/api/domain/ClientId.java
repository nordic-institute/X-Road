/*
 * The MIT License
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

import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.NoCoverage;

import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
public abstract class ClientId extends XRoadId implements ee.ria.xroad.common.identifier.ClientId {

    protected ClientId() {
        //for JPA
    }

    protected ClientId(XRoadObjectType objectType, String xRoadInstance, String memberClass, String memberCode) {
        super(objectType, xRoadInstance, memberClass);
        setMemberCode(memberCode);
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

    @SuppressWarnings("checkstyle:MissingSwitchDefault")
    public static ClientId ensure(ee.ria.xroad.common.identifier.ClientId identifier) {
        return Optional.of(identifier)
                .filter(ClientId.class::isInstance)
                .map(ClientId.class::cast)
                .orElseGet(() -> switch (identifier.getObjectType()) {
                    case MEMBER -> MemberId.create(identifier);
                    case SUBSYSTEM -> SubsystemId.create(identifier);
                    case null, default -> throwIllegalObjectType(identifier);
                });
    }

    private static ClientId throwIllegalObjectType(ee.ria.xroad.common.identifier.ClientId identifier) {
        throw new IllegalArgumentException("illegal object type: " + identifier.getObjectType());
    }

}
