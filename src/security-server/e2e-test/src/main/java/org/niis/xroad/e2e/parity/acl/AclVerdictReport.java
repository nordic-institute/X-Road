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

package org.niis.xroad.e2e.parity.acl;

import org.niis.xroad.e2e.parity.acl.AclScenarioVerdicts.Outcome;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The verdict of a parity row as one tab-separated line: the row id, {@code MATCH} or {@code MISMATCH},
 * the verdicts the row expected, and the verdicts its requests ended in. A row matches only when every request
 * reached its verdict and carried its notice. A row that stops at its first mismatch lists only the requests it
 * polled.
 */
public final class AclVerdictReport {

    public static final String HEADER = "row\tresult\texpected\tobserved";

    private static final String SEPARATOR = "\t";
    private static final String LIST_SEPARATOR = ",";

    private AclVerdictReport() {
    }

    /** The line of a row that sent requests. */
    public static String line(String scenarioId, List<Outcome> outcomes) {
        var matched = AclScenarioVerdicts.failureMessage(scenarioId, outcomes).isEmpty();
        return line(scenarioId, matched,
                outcomes.stream().map(outcome -> outcome.check().expected().name()).collect(Collectors.joining(LIST_SEPARATOR)),
                outcomes.stream().map(outcome -> outcome.result().last().name()).collect(Collectors.joining(LIST_SEPARATOR)));
    }

    /** The line of a row whose outcome is the answer of the admin API to a grant. */
    public static String adminGrantLine(String scenarioId, String expected, String observed) {
        return line(scenarioId, expected.equals(observed), expected, observed);
    }

    /** Creates the file, or empties it, and writes the header. */
    public static void start(Path file) {
        write(file, HEADER, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** Appends the line to a file that {@link #start(Path) start} has created. */
    public static void append(Path file, String line) {
        write(file, line, StandardOpenOption.APPEND);
    }

    private static void write(Path file, String text, StandardOpenOption mode) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, text + System.lineSeparator(), StandardOpenOption.CREATE, mode);
        } catch (IOException e) {
            throw new UncheckedIOException("writing the verdicts of the parity rows to " + file + " failed", e);
        }
    }

    private static String line(String scenarioId, boolean matched, String expected, String observed) {
        return String.join(SEPARATOR, scenarioId, matched ? "MATCH" : "MISMATCH", expected, observed);
    }
}
