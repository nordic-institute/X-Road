/*
 * The MIT License
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

package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.identifier.GlobalGroupId;

import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.identifier.XRoadId.ENCODED_ID_SEPARATOR;

/**
 * Helper to convert GlobalGroups
 */
@Component
public class GlobalGroupConverter {
    private static final int INSTANCE_INDEX = 0;
    private static final int GLOBALGROUP_CODE_INDEX = 1;

    /**
     * Convert GlobalGroupId into encoded id string
     * @return String
     */
    public String convertId(GlobalGroupId globalGroupId) {
        return convertId(globalGroupId, false);
    }

    /**
     * Convert GlobalGroupId into encoded id string
     * @param globalGroupId
     * @return String
     */
    public String convertId(GlobalGroupId globalGroupId, boolean includeType) {
        StringBuilder builder = new StringBuilder();
        if (includeType) {
            builder.append(globalGroupId.getObjectType())
                    .append(ENCODED_ID_SEPARATOR);
        }
        builder.append(globalGroupId.getXRoadInstance())
                .append(ENCODED_ID_SEPARATOR)
                .append(globalGroupId.getGroupCode());
        return builder.toString().trim();
    }

    /**
     * Convert encoded global group id into GlobalGroupId
     * @param encodedId
     * @return {@link GlobalGroupId}
     */
    public GlobalGroupId.Conf convertId(String encodedId) {
        if (!isEncodedGlobalGroupId(encodedId)) {
            throw new BadRequestException("Invalid global group id " + encodedId);
        }
        List<String> parts = Arrays.asList(encodedId.split(String.valueOf(ENCODED_ID_SEPARATOR)));
        String instance = parts.get(INSTANCE_INDEX);
        String groupCode = parts.get(GLOBALGROUP_CODE_INDEX);
        return GlobalGroupId.Conf.create(instance, groupCode);
    }

    public boolean isEncodedGlobalGroupId(String encodedId) {
        int separators = FormatUtils.countOccurences(encodedId, ENCODED_ID_SEPARATOR);
        return separators == GLOBALGROUP_CODE_INDEX;
    }

}
