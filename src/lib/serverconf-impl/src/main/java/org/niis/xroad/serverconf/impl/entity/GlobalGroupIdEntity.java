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
package org.niis.xroad.serverconf.impl.entity;

import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.NoCoverage;
import ee.ria.xroad.common.util.Validation;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import static ee.ria.xroad.common.util.Validation.validateArgument;
import static org.niis.xroad.serverconf.impl.entity.GlobalGroupIdEntity.DISCRIMINATOR_VALUE;

@Getter
@Setter
@Entity
@DiscriminatorValue(DISCRIMINATOR_VALUE)
public class GlobalGroupIdEntity extends XRoadIdEntity implements ee.ria.xroad.common.identifier.GlobalGroupId {
    public static final String DISCRIMINATOR_VALUE = "GG";

    protected GlobalGroupIdEntity(XRoadObjectType objectType, String xRoadInstance, String groupCode) {
        super(DISCRIMINATOR_VALUE, objectType, xRoadInstance, null, groupCode);
    }

    public GlobalGroupIdEntity() {
    }

    public static GlobalGroupIdEntity create(ee.ria.xroad.common.identifier.GlobalGroupId identifier) {
        Validation.validateArgument("identifier", identifier);

        return create(identifier.getXRoadInstance(), identifier.getGroupCode());
    }

    public static GlobalGroupIdEntity create(String xRoadInstance, String groupCode) {
        validateArgument("xRoadInstance", xRoadInstance);
        validateArgument("groupCode", groupCode);
        return new GlobalGroupIdEntity(XRoadObjectType.GLOBALGROUP, xRoadInstance, groupCode);
    }

    @Override
    @NoCoverage
    public boolean equals(Object obj) {
        return ee.ria.xroad.common.identifier.AbstractGroupId.equals(this, obj);
    }

    @Override
    @NoCoverage
    public int hashCode() {
        return ee.ria.xroad.common.identifier.AbstractGroupId.hashCode(this);
    }
}
