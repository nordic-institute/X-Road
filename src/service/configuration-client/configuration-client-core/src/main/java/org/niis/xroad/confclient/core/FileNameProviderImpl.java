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
package org.niis.xroad.confclient.core;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.globalconf.model.ConfigurationConstants;
import org.niis.xroad.globalconf.model.ConfigurationDirectory;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;
import static org.niis.xroad.globalconf.model.ConfigurationUtils.escapeInstanceIdentifier;

/**
 * Default implementation of file name provider.
 */
@RequiredArgsConstructor
public class FileNameProviderImpl implements FileNameProvider {

    private final String globalConfigurationDirectory;

    @Override
    public Path getFileName(ConfigurationFile file) {
        String fileName = switch (file.getContentIdentifier()) {
            case ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS -> FILE_NAME_PRIVATE_PARAMETERS;
            case ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS -> FILE_NAME_SHARED_PARAMETERS;
            default -> resolveContentFileName(file);
        };

        Path instanceDirectory = resolveInstanceDirectory(file.getInstanceIdentifier(), file);
        return resolveWithinInstanceDirectory(instanceDirectory, fileName);
    }

    @Override
    public Path getConfigurationDirectory(String instanceIdentifier) {
        return resolveInstanceDirectory(instanceIdentifier, null);
    }

    /**
     * Resolves the instance subdirectory, rejecting any instance identifier that is blank or
     * escapes/collapses onto the global configuration root (e.g. {@code null}, {@code ""}, {@code "."}).
     */
    private Path resolveInstanceDirectory(String instanceIdentifier, ConfigurationFile file) {
        String escapedInstance = StringUtils.isBlank(instanceIdentifier)
                ? ""
                : escapeInstanceIdentifier(instanceIdentifier);
        Path root = Paths.get(globalConfigurationDirectory).normalize();
        Path resolved = StringUtils.isBlank(escapedInstance)
                ? root
                : Paths.get(globalConfigurationDirectory, escapedInstance).normalize();
        if (resolved.equals(root) || !resolved.startsWith(root)) {
            throw new CodedException(ErrorCodes.X_GLOBAL_CONF_PART_BLANK_INSTANCE_IDENTIFIER,
                    file != null
                            ? "Configuration part %s has a blank or invalid instance identifier".formatted(file)
                            : "Cannot resolve configuration directory for instance identifier '%s'".formatted(instanceIdentifier));
        }
        return resolved;
    }

    private String resolveContentFileName(ConfigurationFile file) {
        String source = !StringUtils.isBlank(file.getContentFileName())
                ? file.getContentFileName()
                : file.getContentLocation();
        Path name = Paths.get(source).getFileName();
        String fileName = name != null ? name.toString() : "";
        if (StringUtils.isBlank(fileName) || ".".equals(fileName) || "..".equals(fileName)) {
            throw new CodedException(ErrorCodes.X_MALFORMED_GLOBALCONF,
                    "Configuration part %s declares an invalid file name derived from %s".formatted(file, source));
        }
        if (ConfigurationDirectory.isReservedFileName(fileName)) {
            throw new CodedException(ErrorCodes.X_GLOBAL_CONF_PART_RESERVED_FILE_NAME,
                    "Configuration part %s resolves to reserved file name %s".formatted(file, fileName));
        }
        return fileName;
    }

    private Path resolveWithinInstanceDirectory(Path instanceDirectory, String fileName) {
        Path resolved = instanceDirectory.resolve(fileName).normalize();
        if (!resolved.startsWith(instanceDirectory) || resolved.equals(instanceDirectory)) {
            throw new CodedException(ErrorCodes.X_GLOBAL_CONF_PART_INVALID_INSTANCE_IDENTIFIER,
                    "Resolved configuration path %s escapes instance directory %s"
                            .formatted(resolved, instanceDirectory));
        }
        return resolved;
    }
}
