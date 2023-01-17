/**
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
package org.niis.xroad.cs.admin.core.entity;

import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.NoCoverage;

import javax.persistence.Entity;

import java.util.Optional;

@Entity
public abstract class ClientIdEntity extends XRoadIdEntity implements ee.ria.xroad.common.identifier.ClientId {

    protected ClientIdEntity() {
        //for JPA
    }

    protected ClientIdEntity(XRoadObjectType objectType, String xRoadInstance, String memberClass, String memberCode) {
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

    public static ClientIdEntity ensure(ee.ria.xroad.common.identifier.ClientId identifier) {
        return Optional.of(identifier)
                .filter(ClientIdEntity.class::isInstance)
                .map(ClientIdEntity.class::cast)
                .orElseGet(() -> {
                    XRoadObjectType objectType = identifier.getObjectType();
                    if (objectType != null) {
                        if (objectType == XRoadObjectType.MEMBER) {
                            return MemberIdEntity.create(identifier);
                        } else if (objectType == XRoadObjectType.SUBSYSTEM) {
                            return SubsystemIdEntity.create(identifier);
                        }
                    }
                    throw new IllegalArgumentException("illegal object type: " + objectType);
                });
    }

}
