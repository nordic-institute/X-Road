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

package org.niis.xroad.edc.extension.policy.dataplane.constraint;

import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.extension.policy.dataplane.util.DataPlaneTransferPolicyContext;
import org.niis.xroad.edc.extension.policy.dataplane.util.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class XRoadDataPathConstraintFunctionTest {

    @Mock
    Monitor monitor;
    @Mock
    Permission permission;

    XRoadDataPathConstraintFunction<DataPlaneTransferPolicyContext> function;

    @BeforeEach
    void setUp() {
        function = new XRoadDataPathConstraintFunction<>(monitor);
    }

    @Test
    void evaluateEqOperatorWithExactMatchReturnsTrue() {
        var context = new DataPlaneTransferPolicyContext("CS:ORG:1234:subsystem", new Endpoint("GET", "/api/data"));

        boolean result = function.evaluate(Operator.EQ, "GET /api/data", permission, context);

        assertThat(result).isTrue();
    }

    @Test
    void evaluateEqOperatorWithNonMatchingPathReturnsFalse() {
        var context = new DataPlaneTransferPolicyContext("CS:ORG:1234:subsystem", new Endpoint("GET", "/api/data"));

        boolean result = function.evaluate(Operator.EQ, "GET /api/other", permission, context);

        assertThat(result).isFalse();
    }

    @Test
    void evaluateEqOperatorWithNonMatchingMethodReturnsFalse() {
        var context = new DataPlaneTransferPolicyContext("CS:ORG:1234:subsystem", new Endpoint("POST", "/api/data"));

        boolean result = function.evaluate(Operator.EQ, "GET /api/data", permission, context);

        assertThat(result).isFalse();
    }

    @Test
    void evaluateIsAnyOfOperatorMatchReturnsTrue() {
        var context = new DataPlaneTransferPolicyContext("CS:ORG:1234:subsystem", new Endpoint("GET", "/api/data"));

        boolean result = function.evaluate(Operator.IS_ANY_OF, "GET /api/data", permission, context);

        assertThat(result).isTrue();
    }

    @Test
    void evaluateEqOperatorWithWildcardMethodReturnsTrue() {
        var context = new DataPlaneTransferPolicyContext("CS:ORG:1234:subsystem", new Endpoint("POST", "/api/data"));

        boolean result = function.evaluate(Operator.EQ, "* /api/data", permission, context);

        assertThat(result).isTrue();
    }

    @Test
    void evaluateEqOperatorWithWildcardPathReturnsTrue() {
        var context = new DataPlaneTransferPolicyContext("CS:ORG:1234:subsystem", new Endpoint("GET", "/anything/here"));

        boolean result = function.evaluate(Operator.EQ, "GET **", permission, context);

        assertThat(result).isTrue();
    }

    @Test
    void evaluateEqOperatorWithGlobPatternReturnsTrue() {
        var context = new DataPlaneTransferPolicyContext("CS:ORG:1234:subsystem", new Endpoint("GET", "/api/users/123"));

        boolean result = function.evaluate(Operator.EQ, "GET /api/users/*", permission, context);

        assertThat(result).isTrue();
    }

    @Test
    void evaluateUnsupportedOperatorReturnsFalse() {
        var context = new DataPlaneTransferPolicyContext("CS:ORG:1234:subsystem", new Endpoint("GET", "/api/data"));

        boolean result = function.evaluate(Operator.NEQ, "GET /api/data", permission, context);

        assertThat(result).isFalse();
        assertThat(context.getProblems()).isNotEmpty();
    }

    @Test
    void evaluateNonStringRightValueReturnsFalse() {
        var context = new DataPlaneTransferPolicyContext("CS:ORG:1234:subsystem", new Endpoint("GET", "/api/data"));

        boolean result = function.evaluate(Operator.EQ, Integer.valueOf(42), permission, context);

        assertThat(result).isFalse();
        assertThat(context.getProblems()).isNotEmpty();
    }
}
