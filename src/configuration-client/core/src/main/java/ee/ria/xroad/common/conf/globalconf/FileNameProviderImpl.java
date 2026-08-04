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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationUtils.escapeInstanceIdentifier;

/**
 * Default implementation of file name provider.
 */
@RequiredArgsConstructor
public class FileNameProviderImpl implements FileNameProvider {

    private static final Set<String> RESERVED_FILE_NAMES = Set.of(
            FILE_NAME_SHARED_PARAMETERS,
            FILE_NAME_PRIVATE_PARAMETERS,
            ConfigurationDirectory.INSTANCE_IDENTIFIER_FILE,
            ConfigurationDirectory.FILES
    );

    private final String globalConfigurationDirectory;

    @Override
    public Path getFileName(ConfigurationFile file) {
        String fileName = switch (file.getContentIdentifier()) {
            case ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS -> FILE_NAME_PRIVATE_PARAMETERS;
            case ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS -> FILE_NAME_SHARED_PARAMETERS;
            default -> resolveContentFileName(file);
        };

        String escapedInstance = escapeInstanceIdentifier(file.getInstanceIdentifier());
        if (StringUtils.isBlank(escapedInstance)) {
            throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_PART_INVALID_INSTANCE_IDENTIFIER)
                    .details("Configuration part %s has a blank instance identifier".formatted(file))
                    .metadataItems(file.getContentLocation())
                    .build();
        }
        return resolveWithinGlobalConf(escapedInstance, fileName);
    }

    @Override
    public Path getConfigurationDirectory(String instanceIdentifier) {
        String escapedInstance = escapeInstanceIdentifier(instanceIdentifier);
        if (StringUtils.isBlank(escapedInstance)) {
            throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_PART_INVALID_INSTANCE_IDENTIFIER)
                    .details("Cannot resolve configuration directory for a blank instance identifier")
                    .build();
        }
        return resolveWithinGlobalConf(escapedInstance);
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
        if (RESERVED_FILE_NAMES.contains(fileName)
                || fileName.endsWith(ConfigurationConstants.FILE_NAME_SUFFIX_METADATA)) {
            throw new CodedException(ErrorCodes.X_GLOBAL_CONF_PART_RESERVED_FILE_NAME,
                    "Configuration part %s resolves to reserved file name %s".formatted(file, fileName));
        }
        return fileName;
    }

    private Path resolveWithinGlobalConf(String... segments) {
        Path root = Paths.get(globalConfigurationDirectory).normalize();
        Path resolved = Paths.get(globalConfigurationDirectory, segments).normalize();
        if (!resolved.startsWith(root)) {
            throw new CodedException(ErrorCodes.X_MALFORMED_GLOBALCONF,
                    "Resolved configuration path %s escapes global configuration directory %s"
                            .formatted(resolved, root));
        }
        return resolved;
    }
}
