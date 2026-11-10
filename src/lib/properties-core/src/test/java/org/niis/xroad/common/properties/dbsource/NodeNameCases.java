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

package org.niis.xroad.common.properties.dbsource;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * The node name test table, one row per branch of the node name rule. Used by {@link NodeNameTest} and the shell
 * parity test so both check the same values. A blank value is accepted and means no name.
 */
@UtilityClass
class NodeNameCases {

    private static final List<Case> CASES = Stream.of(
            Stream.of("node_1", "0", "01", "007", "true", "null", "0123", "1e3", "A_b-c", "0abc", "x".repeat(63))
                    .map(Case::pass),
            Stream.of("", "\t", " ").map(Case::pass),
            Stream.of("_n", "-n", "x".repeat(64), "cs1.example.org", "node 1", "nodé", "node\n", " node_1 ")
                    .map(Case::fail)
    ).flatMap(UnaryOperator.identity()).toList();

    /**
     * @param value    the configured value
     * @param accepted whether the node name rule accepts the value
     */
    record Case(String value, boolean accepted) {

        static Case pass(String value) {
            return new Case(value, true);
        }

        static Case fail(String value) {
            return new Case(value, false);
        }

        @Override
        public String toString() {
            return (accepted ? "accepted" : "rejected") + " \""
                    + value.replace("\n", "\\n").replace("\t", "\\t") + "\"";
        }
    }

    static Stream<Case> all() {
        return CASES.stream();
    }

    static Stream<String> valid() {
        return CASES.stream().filter(testCase -> testCase.accepted() && !testCase.value().isBlank())
                .map(Case::value);
    }

    static Stream<String> blank() {
        return CASES.stream().filter(testCase -> testCase.value().isBlank()).map(Case::value);
    }

    static Stream<String> rejected() {
        return CASES.stream().filter(testCase -> !testCase.accepted()).map(Case::value);
    }
}
