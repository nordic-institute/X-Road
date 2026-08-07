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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.properties.NodeProperties;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.niis.xroad.common.properties.NodeProperties.NODE_TYPE_ENV_VARIABLE;

@ExtendWith(SystemStubsExtension.class)
class AcmeBeanConfigTest {

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @Test
    void doesNotMatchWhenRenewalInactive() {
        assertFalse(schedulingEnabled(false, NodeProperties.NodeType.PRIMARY));
    }

    @Test
    void doesNotMatchOnSecondaryNode() {
        assertFalse(schedulingEnabled(true, NodeProperties.NodeType.SECONDARY));
    }

    @Test
    void matchesOnPrimaryNodeWhenRenewalActive() {
        assertTrue(schedulingEnabled(true, NodeProperties.NodeType.PRIMARY));
    }

    @Test
    void matchesOnStandaloneNodeWhenRenewalActive() {
        assertTrue(schedulingEnabled(true, NodeProperties.NodeType.STANDALONE));
    }

    /**
     * The renewal flag is resolved from the DSL (stored overrides + packaged defaults) rather than the
     * Spring {@code Environment}, so the decision is exercised directly; only the node type still comes
     * from the environment.
     */
    private boolean schedulingEnabled(boolean renewalActive, NodeProperties.NodeType nodeType) {
        variables.set(NODE_TYPE_ENV_VARIABLE, nodeType.name().toLowerCase());

        return AcmeBeanConfig.IsAcmeCertRenewalJobsActive.schedulingEnabled(renewalActive);
    }
}
