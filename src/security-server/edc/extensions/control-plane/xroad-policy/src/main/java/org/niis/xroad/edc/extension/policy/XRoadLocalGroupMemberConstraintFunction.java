package org.niis.xroad.edc.extension.policy;

import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.identifier.LocalGroupId;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintFunction;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.edc.extension.policy.util.PolicyContextHelper;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

;

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
            return PolicyContextHelper.findMemberIdFromContext(context)
                    .map(memberId -> switch (operator) {
                        case EQ, IN -> serverConfProvider.isSubjectAssociatedWithLocalGroup(memberId, localGroupId);
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
