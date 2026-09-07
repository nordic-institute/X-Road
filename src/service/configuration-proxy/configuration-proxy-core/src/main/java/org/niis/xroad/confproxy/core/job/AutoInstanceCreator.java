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
package org.niis.xroad.confproxy.core.job;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.util.AtomicSave;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confclient.common.service.ConfigurationClientService;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.confproxy.common.service.AnchorGenerator;
import org.niis.xroad.confproxy.common.service.ConfProxyInstanceService;
import org.niis.xroad.confproxy.core.service.AnchorDownloader;
import org.niis.xroad.confproxy.core.service.SignerService;
import org.niis.xroad.globalconf.util.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.ANCHOR_XML;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AutoInstanceCreator {
    private final ConfProxyInstanceService confProxyInstanceService;
    private final SignerService signerService;
    private final ConfigurationClientService configurationClientService;
    private final AnchorDownloader anchorDownloader;
    private final AnchorGenerator anchorGenerator;
    private final ConfigurationProxyProperties cpProperties;

    @Startup
    void autoCreate() {
        if (cpProperties.autoInitSoftToken().isPresent()) {
            signerService.initSoftToken(cpProperties.autoInitSoftToken().get().toCharArray());
        }

        if (cpProperties.instances().isEmpty()) {
            log.info("No instances configured for auto instance creation");
            return;
        }

        for (var instance : cpProperties.instances().keySet()) {
            createInstance(instance, cpProperties.instances().get(instance));
        }
    }

    private void createInstance(String name, ConfigurationProxyProperties.Instance config) {
        try {
            if (confProxyInstanceService.exists(name)) {
                log.warn("[{}] Instance already exists. Skipping", name);
                return;
            }

            if (config.signingKeyId().isEmpty() && config.tokenId().isEmpty()) {
                log.warn("[{}] Either token-id or signing-key-id is required. Skipping", name);
                return;
            }

            if (!isTokenActive(config)) {
                if (config.signingKeyId().isPresent()) {
                    log.warn("[{}] Token for signing-key-id: {} isn't active. Skipping", name, config.signingKeyId().get());
                } else {
                    log.warn("[{}] Token for token-id: {} isn't active. Skipping", name, config.tokenId().get());
                }

                return;
            }

            log.info("[{}] Creating instance...", name);
            log.debug("[{}] Downloading configuration anchor from: '{}'", name, config.sourceAnchorFileUri());
            var anchorPath = anchorDownloader.downloadAnchor(config.sourceAnchorFileUri());

            log.debug("[{}] Validating downloaded anchor: {}", name, anchorPath);
            configurationClientService.validate(anchorPath.toString(), false);

            var instance = createInstance(name, anchorPath, config.validityInterval());

            log.debug("[{}] Key config: {}, {}, {}", name, config.tokenId(), config.signingKeyId(), config.keyAlgorithm());

            if (config.signingKeyId().isPresent()) {
                log.debug("[{}] Adding signing key: {}", name, config.signingKeyId().get());
                addSigningKey(instance, config.signingKeyId().get());
            } else {
                log.debug("[{}] Creating and adding signing key for token id: {}", name, config.tokenId().get());
                addSigningKey(instance, config.tokenId().get(), config.keyAlgorithm());
            }
            log.info("[{}] Instance created.", name);

            generateProxyAnchor(instance);
        } catch (Exception e) {
            log.error("[{}] Instance creation failed", name, e);
        }
    }

    private void generateProxyAnchor(ConfProxyInstance conf) {
        var targetPath = Path.of(conf.getInstanceConfigurationPath(), "proxy_anchor.xml");
        log.debug("[{}] Generating proxy anchor: {}", conf.getInstance(), targetPath);
        try {
            var bytes = anchorGenerator.generateAnchor(conf);
            AtomicSave.execute(targetPath, "tmpanchor", out -> out.write(bytes));
            log.info("[{}] Proxy anchor generated at: {}", conf.getInstance(), targetPath);
        } catch (Exception ex) {
            log.error("[{}] Failed to generate anchor: {}", conf.getInstance(), targetPath, ex);
        }
    }

    private boolean isTokenActive(ConfigurationProxyProperties.Instance config) {
        return config.signingKeyId()
                .map(signerService::isTokenActiveByKeyId)
                .or(() -> config.tokenId()
                        .map(signerService::isTokenActive))
                .orElse(false);
    }

    private void addSigningKey(ConfProxyInstance conf, String tokenId, KeyAlgorithm keyAlgorithm) {
        var keyCert = signerService.createCert(tokenId, keyAlgorithm);
        confProxyInstanceService.addSigningKey(conf, keyCert.keyId(), keyCert.cert());
    }

    private void addSigningKey(ConfProxyInstance conf, String signingKey) {
        var keyCert = signerService.createCert(signingKey);
        confProxyInstanceService.addSigningKey(conf, keyCert.keyId(), keyCert.cert());
    }

    private ConfProxyInstance createInstance(String name, Path sourceAnchorPath, int validityInterval) throws IOException {
        log.debug("[{}] Creating instance directory", name);
        var instance = confProxyInstanceService.newInstance(name);
        log.debug("[{}] Setting validity interval to {}s", name, validityInterval);
        confProxyInstanceService.setValidityIntervalSeconds(instance, validityInterval);
        log.debug("[{}] Copying anchor file: {} to instance directory: {}",
                name, sourceAnchorPath, instance.getInstanceConfigurationPath());

        FileUtils.atomicMoveIfPossible(sourceAnchorPath, Paths.get(instance.getInstanceConfigurationPath(), ANCHOR_XML));

        return instance;
    }

}
