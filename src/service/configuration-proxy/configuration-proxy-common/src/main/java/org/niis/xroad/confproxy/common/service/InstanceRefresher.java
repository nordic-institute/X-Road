/*
 * The MIT License
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

import ee.ria.xroad.common.GlobalConfVersion;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.confproxy.common.utils.ConfProxyUtils;
import org.niis.xroad.globalconf.model.VersionedConfigurationDirectory;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.niis.xroad.common.properties.config.keys.CommonConfigKeys.TEMP_FILES_PATH;
import static org.niis.xroad.confproxy.common.exceptions.ConfProxyErrorCode.GLOBAL_CONF_DISTRIBUTION_ERROR;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class InstanceRefresher {
    private static final String VERSION_TPL = "V%d";

    private final SignerRpcClient signerRpcClient;
    private final SignerSignClient signerSignClient;
    private final ConfClientHelper confClientHelper;
    private final ConfigurationProxyProperties cpProperties;
    private final XRoadConfig xRoadConfig;

    /**
     * Launch the configuration proxy instance. Downloads signed directory,
     * signs its content and moves it to the public distribution directory.
     */
    public final void refresh(ConfProxyInstance proxyInstance) {
        log.debug("Purge outdated generations");
        ConfProxyUtils.purgeOutdatedGenerations(proxyInstance);

        if (!proxyInstance.isReady()) {
            log.info("Proxy instance '{}' is not fully configured. Skipping", proxyInstance.getInstance());
            return;
        }

        var result = new ConfProxyExecutionResult();
        for (int version = GlobalConfVersion.CURRENT_VERSION;
                version >= getMinimumConfigurationProxyGlobalConfigurationVersion();
                version--) {
            log.debug("Download global configuration version {}. Minimum version {}",
                    version, getMinimumConfigurationProxyGlobalConfigurationVersion());

            try {
                VersionedConfigurationDirectory confDir = download(proxyInstance, version);
                log.debug("Create output builder");
                var output = OutputBuilder.build(
                        confDir,
                        version,
                        proxyInstance,
                        cpProperties.address(),
                        cpProperties.getHashAlgorithmUri(),
                        cpProperties.getSignatureDigestAlgorithmId(),
                        xRoadConfig.value(TEMP_FILES_PATH));
                try (output) {
                    log.debug("Build signed directory");
                    output.buildSignedDirectory(signerRpcClient, signerSignClient);
                    output.move();
                    log.debug("Finished execute");
                }
                log.info("Successfully distributed global configuration version {}", version);
                result.markSuccessful();
            } catch (Exception e) {
                log.warn("Failed to distribute global configuration version {}", version, e);
                result.addFailedVersion(version);
            }
        }
        if (!result.isSuccess() && !result.getFailedVersions().isEmpty()) {
            throw XrdRuntimeException.systemException(GLOBAL_CONF_DISTRIBUTION_ERROR)
                    .details("Error distributing any global configuration version: "
                            + StringUtils.join(result.getFailedVersions(), ","))
                    .build();
        }
    }

    /**
     * Downloads the global configuration to configuration download path e.g. /etc/xroad/globalconf,
     * according to the instance configuration.
     * @return downloaded configuration directory
     */
    private VersionedConfigurationDirectory download(ConfProxyInstance proxyInstance, int version) throws IOException {
        log.debug("Create directories");
        var downloadPath = getConfigurationDownloadPath(proxyInstance.getInstance(), version);
        Files.createDirectories(Paths.get(downloadPath));
        return confClientHelper.downloadConfiguration(downloadPath, proxyInstance.getProxyAnchorPath(), version);
    }

    /**
     * Gets the path to the directory which should hold the downloaded global
     * configuration files for this configuration proxy instance.
     * @return download path for the global configuration files
     */
    private String getConfigurationDownloadPath(String instance, int version) {
        return Paths.get(cpProperties.globalConfDownloadPath(), instance, VERSION_TPL.formatted(version)).toString();
    }

    public int getMinimumConfigurationProxyGlobalConfigurationVersion() {
        var version = cpProperties.minimumGlobalConfigurationVersion();
        // check that it is a valid looking version number
        checkVersionValidity(version);
        // ignore the versions that are no longer supported
        if (version < GlobalConfVersion.MINIMUM_SUPPORTED_VERSION) {
            version = GlobalConfVersion.MINIMUM_SUPPORTED_VERSION;
        }
        return version;
    }

    private static void checkVersionValidity(int min) {
        if (min > GlobalConfVersion.CURRENT_VERSION || min < 1) {
            throw new IllegalArgumentException("Illegal minimum global configuration version in system parameters");
        }
    }
}
