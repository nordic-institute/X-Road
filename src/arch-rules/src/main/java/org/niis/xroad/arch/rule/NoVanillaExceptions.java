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
package org.niis.xroad.arch.rule;

import com.societegenerale.commons.plugin.rules.ArchRuleTest;
import com.societegenerale.commons.plugin.service.ScopePathProvider;
import com.societegenerale.commons.plugin.utils.ArchUtils;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.niis.xroad.arch.ArchUnitSuppressionHelper;

import java.util.Collection;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

public class NoVanillaExceptions implements ArchRuleTest {
    private static final String RULE_NAME = NoVanillaExceptions.class.getSimpleName();

    private static final Set<Class<?>> VANILLA_EXCEPTIONS = Set.of(
            Exception.class,
            RuntimeException.class
    );

    private static final Set<String> EXCLUDED_PACKAGES = Set.of(
            "org.niis.xroad.signer.proto",
            "org.niis.xroad.signer.protocol.dto",
            "org.niis.xroad.proxy.proto"
    );

    @Override
    public void execute(String packagePath, ScopePathProvider scopePathProvider, Collection<String> excludedPaths) {
        methods().should(new NoVanillaExceptionsCondition())
                .because("Vanilla exceptions provide poor error context. Use custom exceptions with meaningful messages instead.")
                .allowEmptyShould(true)
                .check(ArchUtils.importAllClassesInPackage(scopePathProvider.getMainClassesPath(), packagePath, excludedPaths));
    }

    static class NoVanillaExceptionsCondition extends ArchCondition<JavaMethod> {

        NoVanillaExceptionsCondition() {
            super("not throw vanilla exceptions");
        }

        @Override
        public void check(JavaMethod javaMethod, ConditionEvents events) {
            // Skip methods that are excluded from this rule
            if (isExcluded(javaMethod)) {
                return;
            }

            // Check if the method body throws any vanilla runtime exceptions
            checkMethodBodyForVanillaExceptions(javaMethod, events);
        }

        private void checkMethodBodyForVanillaExceptions(JavaMethod javaMethod, ConditionEvents events) {
            // Check for constructor calls that create vanilla exceptions
            for (JavaCall<?> call : javaMethod.getCallsFromSelf()) {
                if (call instanceof JavaMethodCall) {
                    continue;
                }

                JavaClass targetType = call.getTarget().getOwner();

                if (isVanillaException(targetType)) {

                    String message = "Class '%s' method '%s' creates vanilla exception '%s' at line %d. "
                            + "Use custom exceptions with meaningful context instead.";
                    events.add(SimpleConditionEvent.violated(javaMethod,
                            message.formatted(javaMethod.getOwner().getFullName(), javaMethod.getName(), targetType.getFullName(),
                                    call.getLineNumber())));
                }
            }

            // Also check the throws clause as a fallback
            checkThrowsClause(javaMethod, events);
        }

        private void checkThrowsClause(JavaMethod javaMethod, ConditionEvents events) {
            // Fallback: Check if the method declares vanilla exceptions in throws clause
            for (JavaClass exceptionType : javaMethod.getExceptionTypes()) {
                if (isVanillaException(exceptionType)) {
                    String message = "Class '%s' method '%s' declares vanilla exception '%s' in throws clause. "
                            + "Use custom exceptions with meaningful context instead.";
                    events.add(SimpleConditionEvent.violated(javaMethod,
                            message.formatted(javaMethod.getOwner().getFullName(), javaMethod.getName(), exceptionType.getFullName())));
                }
            }
        }

        private boolean isExcluded(JavaMethod javaMethod) {
            // Skip methods that are excluded from this rule
            if (ArchUnitSuppressionHelper.isSuppressed(javaMethod, RULE_NAME)) {
                return true;
            }

            // Skip generated code (JAXB, OpenAPI, etc.)
            if (isGeneratedCode(javaMethod.getOwner())) {
                return true;
            }

            return false;
        }

        private boolean isGeneratedCode(JavaClass javaClass) {

            // Exclude all JAXB adapter classes (both generated and hand-written)
            // since they are required to throw Exception by the JAXB API contract
            if (javaClass.isAssignableTo(jakarta.xml.bind.annotation.adapters.XmlAdapter.class)) {
                return true;
            }
            if (EXCLUDED_PACKAGES.contains(javaClass.getPackageName())) {
                return true;
            }
            return false;
        }

        private boolean isVanillaException(JavaClass javaClass) {
            // Check if the JavaClass represents exactly one of our vanilla exceptions
            // We use exact matching to avoid catching custom exceptions that extend vanilla ones
            String className = javaClass.getFullName();
            for (Class<?> vanillaException : VANILLA_EXCEPTIONS) {
                if (className.equals(vanillaException.getName())) {
                    return true;
                }
            }
            return false;
        }


    }
}
