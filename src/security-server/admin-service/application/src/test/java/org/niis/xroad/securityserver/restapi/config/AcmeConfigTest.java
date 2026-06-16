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
package org.niis.xroad.securityserver.restapi.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static ee.ria.xroad.common.SystemProperties.NODE_TYPE;
import static ee.ria.xroad.common.SystemProperties.NodeType.MASTER;
import static ee.ria.xroad.common.SystemProperties.NodeType.SLAVE;
import static ee.ria.xroad.common.SystemProperties.NodeType.STANDALONE;
import static ee.ria.xroad.common.SystemProperties.PROXY_UI_API_ACME_RENEWAL_ACTIVE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AcmeConfigTest {

    private final AcmeConfig.IsAcmeCertRenewalJobsActive isAcmeCertRenewalJobsActive =
            new AcmeConfig.IsAcmeCertRenewalJobsActive();

    @AfterEach
    void clearProperties() {
        System.clearProperty(PROXY_UI_API_ACME_RENEWAL_ACTIVE);
        System.clearProperty(NODE_TYPE);
    }

    @Test
    void doesNotMatchWhenRenewalInactive() {
        System.setProperty(PROXY_UI_API_ACME_RENEWAL_ACTIVE, "false");
        System.setProperty(NODE_TYPE, MASTER.toString());

        assertFalse(isAcmeCertRenewalJobsActive.matches(mock(), mock()));
    }

    @Test
    void doesNotMatchOnSlaveNode() {
        System.setProperty(PROXY_UI_API_ACME_RENEWAL_ACTIVE, "true");
        System.setProperty(NODE_TYPE, SLAVE.toString());

        assertFalse(isAcmeCertRenewalJobsActive.matches(mock(), mock()));
    }

    @Test
    void matchesOnMasterNodeWhenRenewalActive() {
        System.setProperty(PROXY_UI_API_ACME_RENEWAL_ACTIVE, "true");
        System.setProperty(NODE_TYPE, MASTER.toString());

        assertTrue(isAcmeCertRenewalJobsActive.matches(mock(), mock()));
    }

    @Test
    void matchesOnStandaloneNodeWhenRenewalActive() {
        System.setProperty(PROXY_UI_API_ACME_RENEWAL_ACTIVE, "true");
        System.setProperty(NODE_TYPE, STANDALONE.toString());

        assertTrue(isAcmeCertRenewalJobsActive.matches(mock(), mock()));
    }
}
