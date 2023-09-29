/*
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

import ee.ria.xroad.common.util.NoCoverage;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@RequiredArgsConstructor
public class TypedExampleMatcherExt implements ExampleMatcherExt {

    @NonNull
    private final ExampleMatcher typedExampleMatcher;

    @NonNull
    private final Set<String> ignoredPaths;

    @NonNull
    private final Set<String> onlyPaths;

    TypedExampleMatcherExt(ExampleMatcher typedExampleMatcher) {
        this(typedExampleMatcher, Collections.emptySet(), Collections.emptySet());
    }

    @Override
    @SuppressWarnings("checkstyle:HiddenField")
    public ExampleMatcherExt withOnlyPaths(String... onlyPaths) {
        Assert.notEmpty(onlyPaths, "OnlyPaths must not be empty!");
        Assert.noNullElements(onlyPaths, "OnlyPaths must not contain null elements!");

        Set<String> newOnlyPaths = new LinkedHashSet<>(this.onlyPaths);
        newOnlyPaths.addAll(Arrays.asList(onlyPaths));

        return new TypedExampleMatcherExt(this, Collections.emptySet(), newOnlyPaths);
    }

    @Override
    @SuppressWarnings("checkstyle:HiddenField")
    public ExampleMatcherExt withIgnorePaths(String... ignoredPaths) {
        Assert.notEmpty(ignoredPaths, "IgnoredPaths must not be empty!");
        Assert.noNullElements(ignoredPaths, "IgnoredPaths must not contain null elements!");

        Set<String> newIgnoredPaths = new LinkedHashSet<>(this.ignoredPaths);
        newIgnoredPaths.addAll(Arrays.asList(ignoredPaths));

        return new TypedExampleMatcherExt(this, newIgnoredPaths, Collections.emptySet());
    }

    @Override
    public boolean isIgnoredPath(String path) {
        if (!ignoredPaths.isEmpty()) {
            return ignoredPaths.contains(path);

        } else if (!onlyPaths.isEmpty()) {
            return !onlyPaths.contains(path);

        } else {
            return false;
        }
    }

    @Override
    @NoCoverage
    public ExampleMatcherExt withStringMatcher(StringMatcher defaultStringMatcher) {
        return new TypedExampleMatcherExt(typedExampleMatcher.withStringMatcher(defaultStringMatcher),
                ignoredPaths,
                onlyPaths);
    }

    @Override
    @NoCoverage
    public ExampleMatcherExt withIgnoreCase() {
        return new TypedExampleMatcherExt(typedExampleMatcher.withIgnoreCase(),
                ignoredPaths,
                onlyPaths);
    }

    @Override
    @NoCoverage
    public ExampleMatcherExt withIgnoreCase(boolean defaultIgnoreCase) {
        return new TypedExampleMatcherExt(typedExampleMatcher.withIgnoreCase(defaultIgnoreCase),
                ignoredPaths,
                onlyPaths);
    }

    @Override
    @NoCoverage
    public ExampleMatcherExt withMatcher(String propertyPath,
                                         MatcherConfigurer<GenericPropertyMatcher> matcherConfigurer) {
        return new TypedExampleMatcherExt(typedExampleMatcher.withMatcher(propertyPath, matcherConfigurer),
                ignoredPaths,
                onlyPaths);
    }

    @Override
    @NoCoverage
    public ExampleMatcherExt withMatcher(String propertyPath, GenericPropertyMatcher genericPropertyMatcher) {
        return new TypedExampleMatcherExt(typedExampleMatcher.withMatcher(propertyPath, genericPropertyMatcher),
                ignoredPaths,
                onlyPaths);
    }

    @Override
    @NoCoverage
    public ExampleMatcherExt withTransformer(String propertyPath, PropertyValueTransformer propertyValueTransformer) {
        return new TypedExampleMatcherExt(typedExampleMatcher.withTransformer(propertyPath, propertyValueTransformer),
                ignoredPaths,
                onlyPaths);
    }

    @Override
    public ExampleMatcherExt withIgnoreCase(String... propertyPaths) {
        return new TypedExampleMatcherExt(typedExampleMatcher.withIgnoreCase(propertyPaths),
                ignoredPaths,
                onlyPaths);
    }

    @Override
    @NoCoverage
    public ExampleMatcherExt withIncludeNullValues() {
        return new TypedExampleMatcherExt(typedExampleMatcher.withIncludeNullValues(),
                ignoredPaths,
                onlyPaths);
    }

    @Override
    @NoCoverage
    public ExampleMatcherExt withIgnoreNullValues() {
        return new TypedExampleMatcherExt(typedExampleMatcher.withIgnoreNullValues(),
                ignoredPaths,
                onlyPaths);
    }

    @Override
    public ExampleMatcherExt withNullHandler(NullHandler nullHandler) {
        return new TypedExampleMatcherExt(typedExampleMatcher.withNullHandler(nullHandler),
                ignoredPaths,
                onlyPaths);
    }

    @Override
    @NoCoverage
    public NullHandler getNullHandler() {
        return typedExampleMatcher.getNullHandler();
    }

    @Override
    @NoCoverage
    public StringMatcher getDefaultStringMatcher() {
        return typedExampleMatcher.getDefaultStringMatcher();
    }

    @Override
    @NoCoverage
    public boolean isIgnoreCaseEnabled() {
        return typedExampleMatcher.isIgnoreCaseEnabled();
    }

    @Override
    @NoCoverage
    public Set<String> getIgnoredPaths() {
        return typedExampleMatcher.getIgnoredPaths();
    }

    @Override
    @NoCoverage
    public PropertySpecifiers getPropertySpecifiers() {
        return typedExampleMatcher.getPropertySpecifiers();
    }

    @Override
    @NoCoverage
    public MatchMode getMatchMode() {
        return typedExampleMatcher.getMatchMode();
    }
}
