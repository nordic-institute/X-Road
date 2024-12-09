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

import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.identifier.LocalGroupId;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintFunction;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.edc.extension.policy.dataplane.util.PolicyContextData;

import java.util.Optional;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.niis.xroad.edc.extension.policy.dataplane.util.PolicyContextHelper.parseClientId;

@RequiredArgsConstructor
public class XRoadLocalGroupMemberConstraintFunction implements AtomicConstraintFunction<Permission> {

    static final String KEY = "xroad:localGroupMember";

    private final ServerConfProvider serverConfProvider;

    private final Monitor monitor;

    @Override
    public boolean evaluate(Operator operator, Object rightValue, Permission rule, PolicyContext context) {
        var stopWatch = StopWatch.createStarted();
        try {
            if (!(rightValue instanceof String globalGroupCode)) {
                context.reportProblem("Right-value expected to be String but was " + rightValue.getClass());
                return false;
            }
            LocalGroupId localGroupId = parseLocalGroup(globalGroupCode);
            String clientIdString = context.getContextData(PolicyContextData.class).clientId();
            return Optional.of(parseClientId(clientIdString))
                    .map(clientId -> switch (operator) {
                        case EQ, IN -> serverConfProvider.isSubjectInLocalGroup(clientId, localGroupId);
                        default -> {
                            context.reportProblem("Unsupported operator: " + operator);
                            yield false;
                        }
                    }).orElse(false);
        } finally {
            monitor.debug("XRoadLocalGroupMemberConstraintFunction took " + stopWatch.getTime(MILLISECONDS) + " ms");
        }
    }

    private LocalGroupId parseLocalGroup(String localGroupCode) {
        return LocalGroupId.Conf.create(localGroupCode);
    }

}
