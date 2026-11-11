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
package org.niis.xroad.configuration.migration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.properties.dbsource.InvalidNodeNameException;
import org.niis.xroad.common.properties.dbsource.NodeName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Carries the X-Road 7 Central Server node name from {@code [center] ha-node-name} in local.ini
 * to {@code [node] name} in node.ini. The name is checked against the node name rule before anything is written.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NodeNameMigrator {

    static final String LEGACY_SECTION = "center";
    static final String LEGACY_KEY = "ha-node-name";
    static final String NODE_SECTION = "node";
    static final String NODE_KEY = "name";

    private static final String NODE_SECTION_HEADER = "[" + NODE_SECTION + "]";
    /** The header lines crudini, which the services use to read node.ini, reads as section node. */
    private static final Pattern NODE_HEADER_LINE = Pattern.compile("^\\[" + NODE_SECTION + "]\\s*(?:[;#].*)?$");
    private static final Pattern NODE_LIKE_HEADER_LINE = Pattern.compile("^\\s*\\[\\s*" + NODE_SECTION + "\\s*].*$");
    private static final Pattern NODE_KEY_LINE = Pattern.compile("^\\s*" + NODE_KEY + "\\s*[=:](.*)$");

    /**
     * Copies the node name; does nothing when local.ini has no name or node.ini already holds the same name.
     *
     * @throws MigrationException if local.ini cannot be read, the name breaks the node name rule, node.ini holds
     *                            a different name, has a section header that looks like [node] but is not read as
     *                            section node, or cannot be read or written; node.ini is then unchanged
     */
    static void migrate(Path localIni, Path nodeIni) {
        var legacyName = readLegacyName(localIni);
        if (legacyName.isEmpty()) {
            log.info("{} has no [{}] {}, node.ini is left unchanged", localIni, LEGACY_SECTION, LEGACY_KEY);
            return;
        }
        var name = legacyName.get();
        validate(name, localIni, nodeIni);

        var lines = readLines(nodeIni);
        var existing = existingName(lines, nodeIni);
        if (existing.isPresent()) {
            if (existing.get().equals(name)) {
                log.info("{} already holds [{}] {} = {}, nothing to do", nodeIni, NODE_SECTION, NODE_KEY, name);
                return;
            }
            throw new MigrationException(("%s holds [%s] %s = %s, but %s has [%s] %s = %s. Nothing was written. "
                    + "Make the two names equal, or remove the name from %s")
                    .formatted(nodeIni, NODE_SECTION, NODE_KEY, existing.get(), localIni, LEGACY_SECTION, LEGACY_KEY, name,
                            nodeIni));
        }

        write(nodeIni, withNodeName(lines, name, nodeIni));
        log.info("Wrote [{}] {} = {} to {}", NODE_SECTION, NODE_KEY, name, nodeIni);
    }

    private static Optional<String> readLegacyName(Path localIni) {
        var parsed = new IniUtil().load(localIni.toString()).parsedContent();
        if (!parsed.getSections().contains(LEGACY_SECTION)) {
            return Optional.empty();
        }
        return Optional.ofNullable(parsed.getSection(LEGACY_SECTION).getString(LEGACY_KEY)).filter(StringUtils::isNotBlank);
    }

    private static void validate(String name, Path localIni, Path nodeIni) {
        try {
            NodeName.resolve(name);
        } catch (InvalidNodeNameException _) {
            throw new MigrationException(("Invalid node name '%s' in %s, key [%s] %s: it %s. Nothing was written to %s. "
                    + "Renaming the node leaves the database rows keyed by the old name behind")
                    .formatted(name, localIni, LEGACY_SECTION, LEGACY_KEY, NodeName.RULE, nodeIni));
        }
    }

    private static List<String> readLines(Path nodeIni) {
        if (!Files.exists(nodeIni)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(Files.readAllLines(nodeIni));
        } catch (IOException e) {
            throw new MigrationException("Failed to read %s".formatted(nodeIni), e);
        }
    }

    private static void write(Path nodeIni, List<String> lines) {
        try {
            Files.write(nodeIni, lines);
        } catch (IOException e) {
            throw new MigrationException("Failed to write %s".formatted(nodeIni), e);
        }
    }

    private static Optional<String> existingName(List<String> lines, Path nodeIni) {
        var header = indexOfNodeSection(lines, nodeIni);
        if (header < 0) {
            return Optional.empty();
        }
        for (int i = header + 1; i < lines.size() && !lines.get(i).strip().startsWith("["); i++) {
            var key = NODE_KEY_LINE.matcher(lines.get(i));
            if (key.matches()) {
                return Optional.of(key.group(1).strip()).filter(StringUtils::isNotBlank);
            }
        }
        return Optional.empty();
    }

    private static List<String> withNodeName(List<String> lines, String name, Path nodeIni) {
        var entry = NODE_KEY + "=" + name;
        var header = indexOfNodeSection(lines, nodeIni);
        if (header < 0) {
            if (!lines.isEmpty() && !lines.getLast().isBlank()) {
                lines.add("");
            }
            lines.add(NODE_SECTION_HEADER);
            lines.add(entry);
            return lines;
        }
        for (int i = header + 1; i < lines.size() && !lines.get(i).strip().startsWith("["); i++) {
            if (NODE_KEY_LINE.matcher(lines.get(i)).matches()) {
                lines.set(i, entry);
                return lines;
            }
        }
        lines.add(header + 1, entry);
        return lines;
    }

    private static int indexOfNodeSection(List<String> lines, Path nodeIni) {
        int header = -1;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (NODE_HEADER_LINE.matcher(line).matches()) {
                if (header < 0) {
                    header = i;
                }
            } else if (NODE_LIKE_HEADER_LINE.matcher(line).matches()) {
                throw new MigrationException(("%s line %d '%s' looks like the [%s] section header, but crudini, which reads "
                        + "node.ini at runtime, does not read it as section %s. Nothing was written. "
                        + "Write the header as [%s] at the start of the line")
                        .formatted(nodeIni, i + 1, line.strip(), NODE_SECTION, NODE_SECTION, NODE_SECTION));
            }
        }
        return header;
    }
}
