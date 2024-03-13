package org.niis.xroad.edc.extension.messagelog;

import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.iam.RequestScope;

import java.util.Set;
import java.util.function.BiFunction;

public record DefaultScopeExtractor(Set<String> defaultScopes) implements BiFunction<Policy, PolicyContext, Boolean> {
    @Override
    public Boolean apply(Policy policy, PolicyContext policyContext) {
        var scopes = policyContext.getContextData(RequestScope.Builder.class);
        scopes.scope(defaultScopes.iterator().next());
        return true;
    }
}
