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
package org.niis.xroad.configuration.migration;

import org.niis.xroad.migration.utils.InstallationType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

record InstallationOption(InstallationType installationType, List<String> remainingArgs) {

    static final String OPTION = "--installation";

    private static final String ACCEPTED_VALUES = Arrays.stream(InstallationType.values())
            .map(InstallationType::getCode)
            .collect(Collectors.joining(", "));

    /**
     * Removes the option from the arguments and resolves its value. Without the option the installation is a
     * Security Server.
     *
     * @throws MigrationException if the option has no value, an unknown value, or is given more than once
     */
    static InstallationOption extract(String[] args) {
        String value = null;
        boolean given = false;
        List<String> remaining = new ArrayList<>();
        Iterator<String> argIterator = List.of(args).iterator();
        while (argIterator.hasNext()) {
            String arg = argIterator.next();
            boolean separate = OPTION.equals(arg);
            if (!separate && !arg.startsWith(OPTION + "=")) {
                remaining.add(arg);
                continue;
            }
            if (given) {
                throw new MigrationException(OPTION + " may be given only once");
            }
            given = true;
            if (separate) {
                if (!argIterator.hasNext()) {
                    throw new MigrationException(OPTION + " requires a value; accepted values: " + ACCEPTED_VALUES);
                }
                value = argIterator.next();
            } else {
                value = arg.substring(OPTION.length() + 1);
            }
        }
        return new InstallationOption(resolve(given, value), List.copyOf(remaining));
    }

    private static InstallationType resolve(boolean given, String value) {
        if (!given) {
            return InstallationType.SECURITY_SERVER;
        }
        return Arrays.stream(InstallationType.values())
                .filter(type -> type.getCode().equals(value))
                .findFirst()
                .orElseThrow(() -> new MigrationException(
                        "Unknown %s value '%s'; accepted values: %s".formatted(OPTION, value, ACCEPTED_VALUES)));
    }
}
