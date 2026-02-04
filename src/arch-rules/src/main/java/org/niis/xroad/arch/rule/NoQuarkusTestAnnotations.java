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

import com.societegenerale.commons.plugin.model.RootClassFolder;
import com.societegenerale.commons.plugin.rules.ArchRuleTest;
import com.societegenerale.commons.plugin.service.ScopePathProvider;
import com.societegenerale.commons.plugin.utils.ArchUtils;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.niis.xroad.arch.ArchUnitSuppressionHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ArchUnit rule that forbids the use of Quarkus test annotations.
 *
 * <p>Quarkus test annotations ({@code @QuarkusTest}, {@code @QuarkusMainTest}, {@code @QuarkusIntegrationTest})
 * use a custom classloader that differs from the production runtime. This causes:
 * <ul>
 *   <li>ClassNotFoundException for reflective access that works in production</li>
 *   <li>CDI discovery differences</li>
 *   <li>Configuration resolution differences</li>
 *   <li>Flaky behavior across Quarkus version upgrades</li>
 * </ul>
 *
 * <p>Use container-based integration tests instead, which test against actual built images
 * and provide higher fidelity verification.
 */
public class NoQuarkusTestAnnotations implements ArchRuleTest {

    private static final String RULE_NAME = NoQuarkusTestAnnotations.class.getSimpleName();

    private static final Set<String> FORBIDDEN_ANNOTATIONS = Set.of(
            "io.quarkus.test.junit.QuarkusTest",
            "io.quarkus.test.junit.main.QuarkusMainTest",
            "io.quarkus.test.junit.QuarkusIntegrationTest"
    );

    private static final String REASON = """
            Quarkus test annotations use a custom classloader that differs from production runtime, \
            leading to false positives/negatives and flaky behavior across Quarkus version upgrades. \
            Use container-based integration tests (*-int-test modules) instead.""";

    @Override
    public void execute(String packagePath, ScopePathProvider scopePathProvider, Collection<String> excludedPaths) {
        // Skip if test classes directory doesn't exist (modules without tests)
        RootClassFolder testClassesFolder = scopePathProvider.getTestClassesPath();
        if (testClassesFolder == null) {
            return;
        }

        String testClassesPath = testClassesFolder.getValue();
        if (testClassesPath == null || !Files.exists(Path.of(testClassesPath))) {
            return;
        }

        JavaClasses classes = ArchUtils.importAllClassesInPackage(testClassesFolder, packagePath, excludedPaths);

        classes()
                .should(new NoQuarkusTestAnnotationsCondition())
                .because(REASON)
                .allowEmptyShould(true)
                .check(classes);
    }

    static class NoQuarkusTestAnnotationsCondition extends ArchCondition<JavaClass> {

        NoQuarkusTestAnnotationsCondition() {
            super("not be annotated with Quarkus test annotations");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            // Skip if suppressed
            if (ArchUnitSuppressionHelper.isSuppressed(javaClass, RULE_NAME)) {
                return;
            }

            for (String forbiddenAnnotation : FORBIDDEN_ANNOTATIONS) {
                if (javaClass.isAnnotatedWith(forbiddenAnnotation)) {
                    String message = "Class <%s> is annotated with @%s which is forbidden. %s"
                            .formatted(javaClass.getFullName(), getSimpleName(forbiddenAnnotation), REASON);
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        }

        private String getSimpleName(String fullyQualifiedName) {
            int lastDot = fullyQualifiedName.lastIndexOf('.');
            return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
        }
    }
}
