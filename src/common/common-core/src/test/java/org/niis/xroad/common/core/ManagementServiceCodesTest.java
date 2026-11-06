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
package org.niis.xroad.common.core;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagementServiceCodesTest {

    @Test
    void allShouldMatchManagementRequestTypeServiceCodes() {
        Set<String> managementServiceCodes = Set.copyOf(ManagementServiceCodes.ALL);
        Set<String> managementRequestTypeCodes = Arrays.stream(ManagementRequestType.values())
                .map(ManagementRequestType::getServiceCode)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(managementRequestTypeCodes, managementServiceCodes);
    }

    @Test
    void dspNegotiatedShouldExcludeDirectRequests() {
        assertFalse(ManagementServiceCodes.DSP_NEGOTIATED.contains(ManagementServiceCodes.AUTH_CERT_REG));
        assertFalse(ManagementServiceCodes.DSP_NEGOTIATED.contains(ManagementServiceCodes.SYSTEM_DID_REG));
        assertEquals(ManagementServiceCodes.ALL.size() - 2, ManagementServiceCodes.DSP_NEGOTIATED.size());
    }

    @Test
    void dspNegotiatedShouldContainMemberDidReg() {
        assertTrue(ManagementServiceCodes.ALL.contains(ManagementServiceCodes.MEMBER_DID_REG));
        assertTrue(ManagementServiceCodes.DSP_NEGOTIATED.contains(ManagementServiceCodes.MEMBER_DID_REG));
    }
}
