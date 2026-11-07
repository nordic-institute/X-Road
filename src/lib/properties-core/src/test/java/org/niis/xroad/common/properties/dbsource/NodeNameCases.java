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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

/**
 * The node name test table, read from {@code node-name-cases.txt}. Used by {@link NodeNameTest} and the
 * shell parity test so both check the same values.
 */
final class NodeNameCases {

    private static final String RESOURCE = "/node-name-cases.txt";

    private static final List<Case> CASES = load();

    private NodeNameCases() {
    }

    enum Category {
        VALID("valid"),
        INVALID_CHARACTERS("invalid-characters"),
        INVALID_YAML_NON_STRING("invalid-yaml-non-string"),
        BLANK("blank");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        private static Category of(String label) {
            return Stream.of(values()).filter(category -> category.label.equals(label)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unknown category " + label));
        }
    }

    /**
     * @param category the class of value
     * @param value    the configured value
     */
    record Case(Category category, String value) {

        /** @return whether the node name rule accepts the value; a blank value is accepted and means no name */
        boolean accepted() {
            return category == Category.VALID || category == Category.BLANK;
        }

        @Override
        public String toString() {
            return category.label + " \"" + value.replace("\n", "\\n").replace("\t", "\\t") + "\"";
        }
    }

    static Stream<Case> all() {
        return CASES.stream();
    }

    static Stream<String> values(Category category) {
        return CASES.stream().filter(testCase -> testCase.category() == category).map(Case::value);
    }

    private static List<Case> load() {
        try (var in = NodeNameCases.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(NodeNameCases::parse)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Case parse(String line) {
        var separator = line.indexOf('|');
        var quoted = line.substring(separator + 1);
        var value = quoted.substring(1, quoted.length() - 1).replace("\\t", "\t").replace("\\n", "\n");
        return new Case(Category.of(line.substring(0, separator)), value);
    }
}
