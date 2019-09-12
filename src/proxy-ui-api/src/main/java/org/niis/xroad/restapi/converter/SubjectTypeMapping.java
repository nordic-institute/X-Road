/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.Getter;
import org.niis.xroad.restapi.openapi.model.SubjectType;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between SubjectType in api (enum) and model (XRoadObjectType)
 */
@Getter
public enum SubjectTypeMapping {
    SUBSYSTEM(XRoadObjectType.SUBSYSTEM, SubjectType.SUBSYSTEM),
    MEMBER(XRoadObjectType.MEMBER, SubjectType.MEMBER),
    LOCALGROUP(XRoadObjectType.LOCALGROUP, SubjectType.LOCALGROUP),
    GLOBALGROUP(XRoadObjectType.GLOBALGROUP, SubjectType.GLOBALGROUP);

    private final XRoadObjectType xRoadObjectType;
    private final SubjectType subjectType;

    SubjectTypeMapping(XRoadObjectType xRoadObjectType, SubjectType subjectType) {
        this.xRoadObjectType = xRoadObjectType;
        this.subjectType = subjectType;
    }

    /**
     * Return matching SubjectType, if any
     * @param xRoadObjectType
     * @return
     */
    public static Optional<SubjectType> map(XRoadObjectType xRoadObjectType) {
        return getFor(xRoadObjectType).map(SubjectTypeMapping::getSubjectType);
    }

    /**
     * Return matching XRoadObjectType, if any
     * @param subjectType
     * @return
     */
    public static Optional<XRoadObjectType> map(SubjectType subjectType) {
        return getFor(subjectType).map(SubjectTypeMapping::getXRoadObjectType);
    }

    /**
     * return SubjectTypeMapping matching the given xRoadObjectType, if any
     * @param xRoadObjectType
     * @return
     */
    public static Optional<SubjectTypeMapping> getFor(XRoadObjectType xRoadObjectType) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.xRoadObjectType.equals(xRoadObjectType))
                .findFirst();
    }

    /**
     * return SubjectTypeMapping matching the given subjectType, if any
     * @param subjectType
     * @return
     */
    public static Optional<SubjectTypeMapping> getFor(SubjectType subjectType) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.subjectType.equals(subjectType))
                .findFirst();
    }

}
