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

package org.niis.xroad.edc.extension.policy;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintFunction;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;
import org.niis.xroad.edc.extension.policy.util.PathGlob;

import java.util.HashSet;

@RequiredArgsConstructor
public class XRoadDataPathConstraintFunction implements AtomicConstraintFunction<Permission> {

    static final String KEY = "xroad:datapath";

    private static final String ANY_METHOD = "*";
    private static final String ANY_PATH = "**";

    private final Monitor monitor;
    private final TypeManager typeManager;

    @Override
    @SneakyThrows
    public boolean evaluate(Operator operator, Object rightValue, Permission rule, PolicyContext context) {
        monitor.debug("XRoadDataPathConstraintFunction.evaluate");

        // rightValue is expected to be a json array of strings
        if (!(rightValue instanceof String allowedPaths)) {
            context.reportProblem("Right-value expected to be String but was " + rightValue.getClass());
            return false;
        }

        String requestedPath = context.getContextData(String.class); //todo: xroad8 put to dedicated dto?
        Endpoint requested = Endpoint.from(requestedPath);

        HashSet<String> allowedEndpointPatterns = typeManager.readValue(allowedPaths, HashSet.class);

        for (String allowedPath : allowedEndpointPatterns) {
            Endpoint allowed = Endpoint.from(allowedPath);
            boolean matches = switch (operator) {
                case IS_ANY_OF -> (ANY_METHOD.equals(allowed.method) || requested.method.equals(allowed.method))
                        && (ANY_PATH.equals(allowed.path) || PathGlob.matches(allowed.path, requested.path));
                default -> {
                    context.reportProblem("Operator " + operator + " not supported");
                    yield false;
                }
            };
            if (matches) {
                return true;
            }
        }
        return false;
    }

    record Endpoint(String method, String path) {
        public static Endpoint from(String param) {
            String[] parts = param.split(" ", 2);
            return new Endpoint(parts[0], parts[1]);
        }
    }

}
