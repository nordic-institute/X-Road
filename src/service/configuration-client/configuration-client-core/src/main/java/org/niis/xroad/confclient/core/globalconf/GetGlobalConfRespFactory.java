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
package org.niis.xroad.confclient.core.globalconf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.niis.xroad.common.core.dto.InMemoryFile;
import org.niis.xroad.confclient.proto.GetGlobalConfResp;
import org.niis.xroad.confclient.proto.GlobalConfFile;
import org.niis.xroad.confclient.proto.GlobalConfInstance;
import org.niis.xroad.globalconf.extension.OcspFetchInterval;
import org.niis.xroad.globalconf.extension.OcspNextUpdate;
import org.niis.xroad.globalconf.model.ConfigurationConstants;
import org.niis.xroad.globalconf.monitoringconf.MonitoringParameters;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static ee.ria.xroad.common.crypto.Digests.hexDigest;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.niis.xroad.globalconf.model.ConfigurationDirectory.INSTANCE_IDENTIFIER_FILE;
import static org.niis.xroad.globalconf.model.VersionedConfigurationDirectory.getVersion;

@Slf4j
@ApplicationScoped
public class GetGlobalConfRespFactory {
    private static final Set<String> GLOBAL_CONF_FILES = Set.of(
            ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS,
            ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS,
            OcspNextUpdate.FILE_NAME_OCSP_NEXT_UPDATE_PARAMETERS,
            OcspFetchInterval.FILE_NAME_OCSP_FETCH_INTERVAL_PARAMETERS,
            MonitoringParameters.FILE_NAME_MONITORING_PARAMETERS);

    GetGlobalConfResp createGlobalConfResp() {
        var confDir = Paths.get(SystemProperties.getConfigurationPath());

        var builder = GetGlobalConfResp.newBuilder();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(confDir, Files::isDirectory)) {
            for (Path instanceDir : stream) {
                log.trace("LoadingPrivateParameters from {}", instanceDir);
                builder.addInstances(loadParameters(instanceDir));
            }
        } catch (IOException e) {
            throw new CodedException(X_MALFORMED_GLOBALCONF, "Failed to read configuration directory", e);
        }
        return builder
                .setInstanceIdentifier(loadInstanceIdentifier())
                .setDateRefreshed(System.currentTimeMillis())
                .build();
    }

    private GlobalConfInstance loadParameters(Path instanceDir) {
        var builder = GlobalConfInstance.newBuilder();

        GLOBAL_CONF_FILES.forEach(fileName -> processFile(builder, instanceDir, fileName));

        Integer version = getVersion(Paths.get(instanceDir.toString(), ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS));
        return builder
                .setInstanceIdentifier(instanceDir.getFileName().toString())
                .setVersion(version == null ? 2 : version)
                .build();
    }

    private void processFile(GlobalConfInstance.Builder builder, Path instanceDir, String fileName) {
        var paramPath = Paths.get(instanceDir.toString(), fileName);
        if (Files.exists(paramPath)) {
            readFileSafely(paramPath)
                    .ifPresent(content ->
                            builder.addFiles(GlobalConfFile.newBuilder()
                                    .setName(fileName)
                                    .setContent(content.content())
                                    .setChecksum(content.checksum())
                                    .build()));
        } else {
            log.trace("File {} does not exist.", paramPath);
        }
    }

    private String loadInstanceIdentifier() {
        var file = Paths.get(SystemProperties.getConfigurationPath(), INSTANCE_IDENTIFIER_FILE);

        log.trace("Loading instance identifier from {}", file);

        try {
            return FileUtils.readFileToString(file.toFile(), UTF_8).trim();
        } catch (Exception e) {
            log.error("Failed to read instance identifier from {}", file, e);

            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not read instance identifier of this security server");
        }
    }

    private Optional<InMemoryFile> readFileSafely(Path filePath) {
        try {
            var content = FileUtils.readFileToString(filePath.toFile(), UTF_8);
            var hash = hexDigest(DigestAlgorithm.MD5, content);
            return Optional.of(new InMemoryFile(content, hash));
        } catch (Exception e) {
            log.error("Failed to read file {}", filePath, e);
            return Optional.empty();
        }
    }


}
