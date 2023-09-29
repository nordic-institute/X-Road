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
package org.niis.xroad.securityserver.restapi.repository;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * test IdentifierRepository
 */
public class IdentifierRepositoryIntegrationTest extends AbstractFacadeMockingTestContext {

    @Autowired
    IdentifierRepository identifierRepository;

    @Test
    public void getClientId() {
        ClientId memberId = identifierRepository.getClientId(
                TestUtils.getClientId("FI:GOV:M1"));
        assertNotNull(memberId);
        assertEquals(XRoadObjectType.MEMBER, memberId.getObjectType());
        assertEquals("M1", memberId.getMemberCode());

        ClientId subsystemId = identifierRepository.getClientId(
                TestUtils.getClientId("FI:GOV:M1:SS2"));
        assertNotNull(subsystemId);
        assertEquals(XRoadObjectType.SUBSYSTEM, subsystemId.getObjectType());
        assertEquals("M1", subsystemId.getMemberCode());

        memberId = identifierRepository.getClientId(
                TestUtils.getClientId("FI:GOV:MFOO"));
        assertNull(memberId);

        subsystemId = identifierRepository.getClientId(
                TestUtils.getClientId("FI:GOV:MFOO:SS2"));
        assertNull(subsystemId);
    }
}


