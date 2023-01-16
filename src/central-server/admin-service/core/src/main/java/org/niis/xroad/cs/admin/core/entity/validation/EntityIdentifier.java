/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.entity.validation;

import org.hibernate.validator.constraints.Length;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@NotBlank
@Length(
        max = 255,
        message = EntityIdentifier.LENGTH_VIOLATION_MESSAGE
)
@Pattern(
        regexp = "^"
                + "\\s*("
                + "(?!"
                + "\\s*" // Don't allow preceding spaces while eliminating
                + "(?:\\.$|\\./|\\.\\.$|\\.\\./)" // Eliminate beginnings ".", "..", "./", "../"
                + ")"
                + "("
                + "(?!/\\.\\s*$|/\\./|/\\.\\.\\s*$|/\\.\\./)" // Eliminate anywhere "/./", "/../" and ending "/.", "/.."
                // and don't allow following spaces while eliminating the
                // ending
                + "[^\\\\\\u0000-\\u001F\\u007F-\\u009F\\u200B\\uFEFF]" // Allow only printable characters
                + ")*"
                + ")?"
                + "$",
        message = EntityIdentifier.PATTERN_VIOLATION_MESSAGE
)
@Target({METHOD, FIELD, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = {})
@Documented
public @interface EntityIdentifier {
    String LENGTH_VIOLATION_MESSAGE = "length must be less or equal to {max}";
    String PATTERN_VIOLATION_MESSAGE = "must not "
            + "start with \".\", \"./\", \"..\", \"../\", "
            + "contain non-printable characters, \"/./\", \"/../\", "
            + "end with \"/.\", \"/..\"";

    String message() default "X-Road identifier is invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
