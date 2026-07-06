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

package org.niis.xroad.edc.extension.policy.controlplane;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;

import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadLocalGroupMemberConstraintFunctionTest {

    @Mock
    ServerConfProvider serverConfProvider;
    @Mock
    Monitor monitor;
    @Mock
    Permission permission;

    XRoadLocalGroupMemberConstraintFunction<CatalogPolicyContext> function;

    @BeforeEach
    void setUp() {
        function = new XRoadLocalGroupMemberConstraintFunction<>(serverConfProvider, monitor);
    }

    @Test
    void evaluateEqOperatorWhenMemberReturnsTrue() {
        when(serverConfProvider.isSubjectInLocalGroup(any(ClientId.class), any(LocalGroupId.class))).thenReturn(true);
        var context = contextWithMember("CS", "ORG", "1234");

        boolean result = function.evaluate(Operator.EQ, "myLocalGroup", permission, context);

        assertThat(result).isTrue();
    }

    @Test
    void evaluateInOperatorWhenMemberReturnsTrue() {
        when(serverConfProvider.isSubjectInLocalGroup(any(ClientId.class), any(LocalGroupId.class))).thenReturn(true);
        var context = contextWithMember("CS", "ORG", "1234");

        boolean result = function.evaluate(Operator.IN, "myLocalGroup", permission, context);

        assertThat(result).isTrue();
    }

    @Test
    void evaluateEqOperatorWhenNotMemberReturnsFalse() {
        when(serverConfProvider.isSubjectInLocalGroup(any(ClientId.class), any(LocalGroupId.class))).thenReturn(false);
        var context = contextWithMember("CS", "ORG", "1234");

        boolean result = function.evaluate(Operator.EQ, "myLocalGroup", permission, context);

        assertThat(result).isFalse();
    }

    @Test
    void evaluateUnsupportedOperatorReturnsFalse() {
        var context = contextWithMember("CS", "ORG", "1234");

        boolean result = function.evaluate(Operator.NEQ, "myLocalGroup", permission, context);

        assertThat(result).isFalse();
        assertThat(context.getProblems()).isNotEmpty();
    }

    @Test
    void evaluateNonStringRightValueReturnsFalse() {
        var context = contextWithMember("CS", "ORG", "1234");

        boolean result = function.evaluate(Operator.EQ, Integer.valueOf(42), permission, context);

        assertThat(result).isFalse();
        assertThat(context.getProblems()).isNotEmpty();
    }

    private CatalogPolicyContext contextWithMember(String xroadInstance, String memberClass, String memberCode) {
        var agent = new ParticipantAgent("test-id", Map.<String, Object>of(), Map.of(
                "xrd:xroadInstance", xroadInstance,
                "xrd:memberClass", memberClass,
                "xrd:memberCode", memberCode));
        return new CatalogPolicyContext(agent);
    }
}
