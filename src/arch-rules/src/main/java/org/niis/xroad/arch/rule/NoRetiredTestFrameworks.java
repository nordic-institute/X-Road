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
import com.tngtech.archunit.core.domain.Dependency;
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
 * ArchUnit rule that forbids depending on the test frameworks retired with {@code tool/test-framework-core}:
 * Selenide, Feign (including Spring Cloud OpenFeign), and Cucumber.
 *
 * <p>RestAssured, gRPC clients, and JUnit 5 with the {@code Step} narrative helper cover their former use
 * cases. The ban applies to test source sets ({@code src/test}, {@code src/intTest}) only; production code
 * (e.g. the Feign-based {@code central-server:admin-service:api-client}) is untouched.
 */
public class NoRetiredTestFrameworks implements ArchRuleTest {

    private static final String RULE_NAME = NoRetiredTestFrameworks.class.getSimpleName();

    private static final Set<String> FORBIDDEN_PACKAGES = Set.of(
            "com.codeborne.selenide",
            "feign",
            "org.springframework.cloud.openfeign",
            "io.cucumber"
    );

    private static final String REASON = """
            Selenide, Feign, and Cucumber were retired with test-framework-core. \
            Use RestAssured/gRPC clients and JUnit 5 with the Step helper instead.""";

    @Override
    public void execute(String packagePath, ScopePathProvider scopePathProvider, Collection<String> excludedPaths) {
        checkSourceSet(scopePathProvider.getTestClassesPath(), packagePath, excludedPaths);
        checkSourceSet(intTestClassesPath(scopePathProvider), packagePath, excludedPaths);
    }

    private void checkSourceSet(RootClassFolder classesFolder, String packagePath, Collection<String> excludedPaths) {
        if (classesFolder == null || classesFolder.getValue() == null || !Files.exists(Path.of(classesFolder.getValue()))) {
            return;
        }

        JavaClasses classes = ArchUtils.importAllClassesInPackage(classesFolder, packagePath, excludedPaths);

        classes().should(new NoRetiredTestFrameworksCondition())
                .because(REASON)
                .allowEmptyShould(true)
                .check(classes);
    }

    private RootClassFolder intTestClassesPath(ScopePathProvider scopePathProvider) {
        RootClassFolder testClassesPath = scopePathProvider.getTestClassesPath();
        if (testClassesPath == null || testClassesPath.getValue() == null) {
            return null;
        }

        String value = testClassesPath.getValue();
        int lastSeparator = value.lastIndexOf('/');
        if (lastSeparator < 0 || !"test".equals(value.substring(lastSeparator + 1))) {
            return null;
        }

        return new RootClassFolder(value.substring(0, lastSeparator + 1) + "intTest");
    }

    static class NoRetiredTestFrameworksCondition extends ArchCondition<JavaClass> {

        NoRetiredTestFrameworksCondition() {
            super("not depend on Selenide, Feign, or Cucumber");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            if (ArchUnitSuppressionHelper.isSuppressed(javaClass, RULE_NAME)) {
                return;
            }

            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                if (isForbidden(targetPackage)) {
                    String message = "Class '%s' depends on retired test framework package '%s' (%s). %s"
                            .formatted(javaClass.getFullName(), targetPackage, dependency.getTargetClass().getFullName(), REASON);
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        }

        private boolean isForbidden(String packageName) {
            return FORBIDDEN_PACKAGES.stream()
                    .anyMatch(forbidden -> packageName.equals(forbidden) || packageName.startsWith(forbidden + "."));
        }
    }
}
