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
package org.niis.xroad.cs.admin.jpa.example;

import org.springframework.data.domain.ExampleMatcher;

public interface ExampleMatcherExt extends ExampleMatcher {

    /**
     * Create a new {@link ExampleMatcherExt} including all non-null properties by default matching <strong>all</strong>
     * predicates derived from the example.
     *
     * @return new instance of {@link ExampleMatcherExt}.
     * @see #matchingAll()
     */
    static ExampleMatcherExt matching() {
        return matchingAll();
    }

    /**
     * Create a new {@link ExampleMatcherExt} including all non-null properties by default matching <strong>any</strong>
     * predicate derived from the example.
     *
     * @return new instance of {@link ExampleMatcherExt}.
     */
    static ExampleMatcherExt matchingAny() {
        return new TypedExampleMatcherExt(ExampleMatcher.matching());
    }

    /**
     * Create a new {@link ExampleMatcherExt} including all non-null properties by default matching <strong>all</strong>
     * predicates derived from the example.
     *
     * @return new instance of {@link ExampleMatcherExt}.
     */
    static ExampleMatcherExt matchingAll() {
        return new TypedExampleMatcherExt(ExampleMatcher.matchingAll());
    }

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with the specified {@code propertyPaths}. This instance is
     * immutable and unaffected by this method call.
     *
     * @param onlyPaths must not be {@literal null} and not empty.
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withOnlyPaths(String... onlyPaths);

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with the specified {@code propertyPaths}. This instance is
     * immutable and unaffected by this method call.
     *
     * @param ignoredPaths must not be {@literal null} and not empty.
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withIgnorePaths(String... ignoredPaths);

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with the specified string matching of
     * {@code defaultStringMatcher}. This instance is immutable and unaffected by this method call.
     *
     * @param defaultStringMatcher must not be {@literal null}.
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withStringMatcher(StringMatcher defaultStringMatcher);

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with ignoring case sensitivity by default. This instance is
     * immutable and unaffected by this method call.
     *
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withIgnoreCase();

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with {@code defaultIgnoreCase}. This instance is immutable and
     * unaffected by this method call.
     *
     * @param defaultIgnoreCase
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withIgnoreCase(boolean defaultIgnoreCase);

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with the specified {@code GenericPropertyMatcher} for the
     * {@code propertyPath}. This instance is immutable and unaffected by this method call.
     *
     * @param propertyPath      must not be {@literal null}.
     * @param matcherConfigurer callback to configure a {@link GenericPropertyMatcher}, must not be {@literal null}.
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withMatcher(String propertyPath, MatcherConfigurer<GenericPropertyMatcher> matcherConfigurer);

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with the specified {@code GenericPropertyMatcher} for the
     * {@code propertyPath}. This instance is immutable and unaffected by this method call.
     *
     * @param propertyPath           must not be {@literal null}.
     * @param genericPropertyMatcher callback to configure a {@link GenericPropertyMatcher}, must not be
     *                               {@literal null}.
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withMatcher(String propertyPath, GenericPropertyMatcher genericPropertyMatcher);

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with the specified {@code PropertyValueTransformer} for the
     * {@code propertyPath}.
     *
     * @param propertyPath             must not be {@literal null}.
     * @param propertyValueTransformer must not be {@literal null}.
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withTransformer(String propertyPath, PropertyValueTransformer propertyValueTransformer);

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with ignore case sensitivity for the {@code propertyPaths}. This
     * instance is immutable and unaffected by this method call.
     *
     * @param propertyPaths must not be {@literal null} and not empty.
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withIgnoreCase(String... propertyPaths);

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with treatment for {@literal null} values of
     * {@link NullHandler#INCLUDE} . This instance is immutable and unaffected by this method call.
     *
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withIncludeNullValues();

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with treatment for {@literal null} values of
     * {@link NullHandler#IGNORE}. This instance is immutable and unaffected by this method call.
     *
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withIgnoreNullValues();

    /**
     * Returns a copy of this {@link ExampleMatcherExt} with the specified {@code nullHandler}. This instance is
     * immutable and unaffected by this method call.
     *
     * @param nullHandler must not be {@literal null}.
     * @return new instance of {@link ExampleMatcherExt}.
     */
    ExampleMatcherExt withNullHandler(NullHandler nullHandler);

}
