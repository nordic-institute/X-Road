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
package org.niis.xroad.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.ArchUnitExcluded;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for managing ArchUnit exclusions and showing deprecation warnings.
 */
@Slf4j
@UtilityClass
public class ArchUnitExclusionHelper {

    /**
     * Checks if a method is excluded from a specific ArchUnit rule.
     *
     * @param method   the method to check
     * @param ruleName the name of the rule to check exclusion for
     * @return true if the method is excluded from the rule
     */
    public static boolean isExcluded(JavaMethod method, String ruleName) {
        // Check method-level exclusions
        if (isExcludedByAnnotation(method, ruleName)) {
            return true;
        }

        // Check class-level exclusions
        JavaClass clazz = method.getOwner();
        return isExcludedByAnnotation(clazz, ruleName);
    }

    /**
     * Checks if a class is excluded from a specific ArchUnit rule.
     *
     * @param clazz    the class to check
     * @param ruleName the name of the rule to check exclusion for
     * @return true if the class is excluded from the rule
     */
    public static boolean isExcluded(JavaClass clazz, String ruleName) {
        return isExcludedByAnnotation(clazz, ruleName);
    }

    /**
     * Checks if an element is excluded by any of the exclusion annotations.
     *
     * @param element  the element to check (can be JavaClass or JavaMethod)
     * @param ruleName the rule name to check
     * @return true if the element is excluded
     */
    private static boolean isExcludedByAnnotation(Object element, String ruleName) {
        // Check for regular exclusion
        if (element instanceof JavaMethod method && method.isAnnotatedWith(ArchUnitExcluded.class)) {
            ArchUnitExcluded exclusion = method.getAnnotationOfType(ArchUnitExcluded.class);
            if (Arrays.asList(exclusion.rules()).contains(ruleName)) {
                logExclusion(method.getFullName(), exclusion, "method");
                return true;
            }
        } else if (element instanceof JavaClass clazz && clazz.isAnnotatedWith(ArchUnitExcluded.class)) {
            ArchUnitExcluded exclusion = clazz.getAnnotationOfType(ArchUnitExcluded.class);
            if (Arrays.asList(exclusion.rules()).contains(ruleName)) {
                logExclusion(clazz.getFullName(), exclusion, "class");
                return true;
            }
        }

        return false;
    }

    /**
     * Logs exclusion information, including deprecation warnings.
     *
     * @param elementName the name of the excluded element
     * @param exclusion   the exclusion annotation (either type)
     * @param elementType the type of element (class/method)
     */
    private static void logExclusion(String elementName, Object exclusion, String elementType) {
        String[] rules;
        String reason;

        if (exclusion instanceof ArchUnitExcluded regular) {
            rules = regular.rules();
            reason = regular.reason();
        } else {
            return; // Should not happen
        }

        log.info("{} '{}' is excluded from ArchUnit rules: {}. Reason: {}",
                elementType,
                elementName,
                Arrays.toString(rules),
                reason);
    }

    /**
     * Gets a list of all excluded elements for a specific rule.
     * This can be used for reporting and migration planning.
     *
     * @param classes  list of classes to check
     * @param ruleName the rule name to check exclusions for
     * @return list of excluded element names
     */
    public static List<String> getExcludedElements(List<JavaClass> classes, String ruleName) {
        return classes.stream()
                .filter(clazz -> isExcluded(clazz, ruleName))
                .map(JavaClass::getFullName)
                .toList();
    }
}
