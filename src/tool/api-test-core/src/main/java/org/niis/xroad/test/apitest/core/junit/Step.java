/*
 * The MIT License
 *
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
package org.niis.xroad.test.apitest.core.junit;

import io.qameta.allure.Allure;

/**
 * BDD-named report steps for plain JUnit5 tests. Each method wraps {@link Allure#step} and prefixes the
 * description with its BDD keyword so that tests read as an ordered Given/When/Then flow and render the same
 * way in the Allure report. Both value-returning and {@link Runnable} forms are supported.
 */
public final class Step {

    private Step() {
    }

    public static <T> T given(String description, ValueStep<T> step) {
        return step("Given " + description, step);
    }

    public static void given(String description, Runnable step) {
        step("Given " + description, step);
    }

    public static <T> T when(String description, ValueStep<T> step) {
        return step("When " + description, step);
    }

    public static void when(String description, Runnable step) {
        step("When " + description, step);
    }

    public static <T> T then(String description, ValueStep<T> step) {
        return step("Then " + description, step);
    }

    public static void then(String description, Runnable step) {
        step("Then " + description, step);
    }

    public static <T> T and(String description, ValueStep<T> step) {
        return step("And " + description, step);
    }

    public static void and(String description, Runnable step) {
        step("And " + description, step);
    }

    private static <T> T step(String description, ValueStep<T> step) {
        return Allure.step(description, step::get);
    }

    private static void step(String description, Runnable step) {
        Allure.step(description, step::run);
    }

    /**
     * Value-returning step body so a step can extract an id or POJO for the next step.
     *
     * @param <T> the produced value type
     */
    @FunctionalInterface
    public interface ValueStep<T> {
        T get() throws Exception;
    }
}
