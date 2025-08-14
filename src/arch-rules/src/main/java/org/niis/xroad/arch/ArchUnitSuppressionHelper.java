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

import com.tngtech.archunit.core.domain.JavaMethod;
import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;

/**
 * Utility class for handling ArchUnit rule suppressions.
 * Uses @ArchUnitSuppressed annotation for reliable suppression with RUNTIME retention.
 */
@UtilityClass
public final class ArchUnitSuppressionHelper {

    /**
     * Checks if a method is suppressed from a specific ArchUnit rule.
     * Uses @ArchUnitSuppressed annotation for reliable suppression.
     *
     * @param javaMethod the method to check
     * @param ruleName   the name of the rule to check suppression for
     * @return true if the method is suppressed from the rule, false otherwise
     */
    public static boolean isSuppressed(JavaMethod javaMethod, String ruleName) {
        if (javaMethod.isAnnotatedWith(ArchUnitSuppressed.class)) {
            ArchUnitSuppressed annotation = javaMethod.getAnnotationOfType(ArchUnitSuppressed.class);
            if (isRuleSuppressedByCustomAnnotation(annotation, ruleName)) {
                return true;
            }
        }

        // Check if the declaring class has @ArchUnitSuppressed
        if (javaMethod.getOwner().isAnnotatedWith(ArchUnitSuppressed.class)) {
            ArchUnitSuppressed annotation = javaMethod.getOwner().getAnnotationOfType(ArchUnitSuppressed.class);
            return isRuleSuppressedByCustomAnnotation(annotation, ruleName);
        }

        return false;
    }

    /**
     * Checks if an ArchUnitSuppressed annotation suppresses a specific rule.
     *
     * @param annotation the ArchUnitSuppressed annotation to check
     * @param ruleName   the name of the rule to check suppression for
     * @return true if the annotation suppresses the rule, false otherwise
     */
    private static boolean isRuleSuppressedByCustomAnnotation(ArchUnitSuppressed annotation, String ruleName) {
        String[] suppressedRules = annotation.value();

        // Empty array means suppress all rules
        if (suppressedRules.length == 0) {
            return true;
        }

        // Check for specific rule suppression
        for (String suppressedRule : suppressedRules) {
            if (suppressedRule.equals(ruleName)) {
                return true;
            }
        }

        return false;
    }


}
