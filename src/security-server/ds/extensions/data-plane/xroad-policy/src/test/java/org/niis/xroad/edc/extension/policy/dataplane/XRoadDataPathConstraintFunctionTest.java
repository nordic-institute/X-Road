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

package org.niis.xroad.edc.extension.policy.dataplane;


import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.extension.policy.dataplane.util.Endpoint;
import org.niis.xroad.edc.extension.policy.dataplane.util.DataPlaneTransferPolicyContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class XRoadDataPathConstraintFunctionTest {

    private final Monitor monitor = mock(Monitor.class);
    private final Permission rule = mock(Permission.class);

    private final TypeManager typeManager = new JacksonTypeManager();

    @Test
    void test() throws Exception {
        assertTrue(evaluate(new Endpoint("GET", "/user/1"), "* **"));
        assertTrue(evaluate(new Endpoint("PUT", "/user/1/name"), "* **"));
        assertTrue(evaluate(new Endpoint("POST", "/users"), "* **"));

        assertFalse(evaluate(new Endpoint("POST", "/users"), "GET /**"));
        assertFalse(evaluate(new Endpoint("PUT", "/users"), "GET /**"));
        assertTrue(evaluate(new Endpoint("GET", "/users/8"), "GET /**"));

        assertTrue(evaluate(new Endpoint("POST", "/user/1/name"), "POST /user/**/name"));
        assertTrue(evaluate(new Endpoint("POST", "/user/2/name"), "POST /user/**/name"));
//        assertTrue(evaluate("POST /user/3/name", "GET /user/**/name", "POST /user/**/name"));
        assertTrue(evaluate(new Endpoint("POST", "/user/1/2/3/name"), "POST /user/**/name"));
        assertFalse(evaluate(new Endpoint("GET", "/user/1/2/3/name"), "POST /user/**/name"));
        assertFalse(evaluate(new Endpoint("GET", "/user/1/2/name"), "POST /user/**/name"));
        assertFalse(evaluate(new Endpoint("GET", "/user/1/name"), "POST /user/**/name"));
    }

    private boolean evaluate(Endpoint requested, String allowed) {
        XRoadDataPathConstraintFunction constraint = new XRoadDataPathConstraintFunction(monitor, typeManager);

        return constraint.evaluate(Operator.EQ, allowed, rule, new DataPlaneTransferPolicyContext(null, requested));
    }

}
