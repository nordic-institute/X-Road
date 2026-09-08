/*
 * The MIT License
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
package org.niis.xroad.confproxy.common.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.DeviationBuilder;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confclient.common.domain.ReturnCode;
import org.niis.xroad.confclient.common.service.ConfigurationClientService;
import org.niis.xroad.globalconf.model.VersionedConfigurationDirectory;

import java.io.IOException;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.confproxy.common.exceptions.ConfClientErrorCode.CONFIGURATION_EXPIRED_ERROR;
import static org.niis.xroad.confproxy.common.exceptions.ConfClientErrorCode.DOWNLOAD_ERROR;
import static org.niis.xroad.confproxy.common.exceptions.ConfClientErrorCode.INVALID_SIGNATURE_ERROR;

/**
 * Provides configuration proxy utility functions.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public final class ConfClientHelper {
    private static final String CONFIGURATION_CLIENT_ERROR = "configuration-client error (exit code %1$d)";

    private final ConfigurationClientService configurationClientService;

    /**
     * Invoke the configuration client script to download the global
     * configuration from the source defined in the provided source anchor.
     * @param path         where the downloaded files should be placed
     * @param sourceAnchor path to the source anchor xml file
     * @return downloaded configuration directory
     */
    public VersionedConfigurationDirectory downloadConfiguration(final String path, final String sourceAnchor, final int version)
            throws IOException {
        log.info("Downloading: from '{}', to '{}', version: {}' ...", sourceAnchor, path, version);

        download(path, sourceAnchor, version);

        return new VersionedConfigurationDirectory(path);
    }

    public VersionedConfigurationDirectory downloadConfiguration(final String path, final String sourceAnchor)
            throws IOException {
        log.info("Downloading: from '{}', to '{}'' ...", sourceAnchor, path);

        download(path, sourceAnchor, 0);

        return new VersionedConfigurationDirectory(path);
    }

    /**
     * Helper method for running the configuration client script.
     */
    private void download(final String path, final String sourceAnchor, final int version) {
        try {
            var result = version > 0
                    ? configurationClientService.download(sourceAnchor, path, version)
                    : configurationClientService.download(sourceAnchor, path);

            switch (result) {
                case RETURN_SUCCESS -> {
                    //Success, do nothing
                }
                case ERROR_CODE_CANNOT_DOWNLOAD_CONF -> throw buildCcError(DOWNLOAD_ERROR, result, ", download failed");
                case ERROR_CODE_EXPIRED_CONF -> throw buildCcError(CONFIGURATION_EXPIRED_ERROR, result, ", configuration is outdated");
                case ERROR_CODE_INVALID_SIGNATURE_VALUE ->
                        throw buildCcError(INVALID_SIGNATURE_ERROR, result, ", configuration is incorrect");
                case ERROR_CODE_INTERNAL -> throw buildCcError(INTERNAL_ERROR, result, null);
                default -> throw XrdRuntimeException
                        .systemInternalError("Failed to download GlobalConf [%s], make sure configuration-client config is correct"
                                .formatted(CONFIGURATION_CLIENT_ERROR.formatted(result.getCode()))
                        );
            }

        } catch (Exception e) {
            log.error("Undetermined ConfigurationClient exitCode", e);
            //undetermined ConfigurationClient exitCode, fail in 'finally'
            throw XrdRuntimeException.systemInternalError("Failed to download GlobalConf", e);
        }
    }

    private XrdRuntimeException buildCcError(DeviationBuilder.ErrorDeviationBuilder error, ReturnCode result, String suffix) {
        return XrdRuntimeException.systemException(error)
                .details(String.format(CONFIGURATION_CLIENT_ERROR, result.getCode()) + (suffix == null ? "" : suffix))
                .build();
    }
}
