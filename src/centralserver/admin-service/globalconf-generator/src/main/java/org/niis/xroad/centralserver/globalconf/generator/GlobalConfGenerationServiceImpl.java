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
package org.niis.xroad.centralserver.globalconf.generator;


import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.ConfigurationConstants;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.GlobalConfGenerationService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.CONF_EXPIRE_INTERVAL_SECONDS;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.CONF_HASH_ALGO_URI;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.CONF_SIGN_CERT_HASH_ALGO_URI;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.CONF_SIGN_DIGEST_ALGO_ID;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.DEFAULT_CONF_EXPIRE_INTERVAL_SECONDS;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.DEFAULT_CONF_HASH_ALGO_URI;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.DEFAULT_CONF_SIGN_CERT_HASH_ALGO_URI;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.DEFAULT_CONF_SIGN_DIGEST_ALGO_ID;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.INSTANCE_IDENTIFIER;

@Component
@Slf4j
@AllArgsConstructor
public class GlobalConfGenerationServiceImpl implements GlobalConfGenerationService {
    private static final int CONFIGURATION_VERSION = 2;

    private SignerProxyFacade signerProxyFacade;
    private SystemParameterService systemParameterService;
    private ConfigurationService configurationService;

    @SneakyThrows
    @Override
    @Scheduled(fixedRate = 60, timeUnit = SECONDS) // TODO make configurable
    @Transactional
    public void generate() {
        log.debug("Generating global configuration");

        var configurationParts = generateConfiguration();
        var configGenerationTime = Instant.now();

        configurationParts.forEach(gp ->
            configurationService.saveConfigurationPart(gp.getContentIdentifier(), gp.getFilename(), gp.getData(), CONFIGURATION_VERSION));



        // TODO split internal and external

        // TODO add optional configuration parts

        var generatedConfDir = Path.of(SystemProperties.getCenterGeneratedConfDir());

        var configDistributor = new ConfigurationDistributor(generatedConfDir, CONFIGURATION_VERSION, configGenerationTime);
        configDistributor.initConfLocation();
        configDistributor.writeConfigurationFiles(configurationParts);


        var directoryContentBuilder = new DirectoryContentBuilder(
                getConfHashAlgoId(),
                Instant.now().plusSeconds(getConfExpireIntervalSeconds()),
                "/" + configDistributor.getSubPath().toString(),
                getInstanceIdentifier())
                .contentParts(configurationParts);
        var directoryContent = directoryContentBuilder.build();

        DirectoryContentSigner directoryContentSigner = new DirectoryContentSigner(
                signerProxyFacade,
                getConfSignDigestAlgoId(),
                getConfSignCertHashAlgoId());


        var keyId = "F397AF7369B15D42D7190E90ECA9508D48275FAB";
        var signedDirectory = directoryContentSigner.createSignedDirectory(directoryContent, keyId, "fixme".getBytes());


        configDistributor.writeDirectoryContentFile("internalconf.tmp", signedDirectory.getBytes(UTF_8));
        configDistributor.moveDirectoryContentFile("internalconf.tmp", "internalconf");


        // TODO write local copy

        // TODO remove old configs
    }

    @SneakyThrows
    private String getConfSignCertHashAlgoId() {
        return CryptoUtils.getAlgorithmId(systemParameterService.getParameterValue(CONF_SIGN_CERT_HASH_ALGO_URI, DEFAULT_CONF_SIGN_CERT_HASH_ALGO_URI));
    }

    private String getConfSignDigestAlgoId() {
        return systemParameterService.getParameterValue(CONF_SIGN_DIGEST_ALGO_ID, DEFAULT_CONF_SIGN_DIGEST_ALGO_ID);
    }

    private String getInstanceIdentifier() {
        return systemParameterService.getParameterValue(INSTANCE_IDENTIFIER, null);
    }

    private int getConfExpireIntervalSeconds() {
        return Integer.parseInt(systemParameterService
                .getParameterValue(CONF_EXPIRE_INTERVAL_SECONDS, DEFAULT_CONF_EXPIRE_INTERVAL_SECONDS.toString()));
    }

    @SneakyThrows
    private String getConfHashAlgoId() {
        return CryptoUtils.getAlgorithmId(systemParameterService.getParameterValue(CONF_HASH_ALGO_URI, DEFAULT_CONF_HASH_ALGO_URI));
    }


    // TODO replace with real configuration generator. When done, also fix fileData saving in ConfigurationServiceImpl.
    private List<ConfigurationPart> generateConfiguration() {
        return List.of(
                ConfigurationPart.builder()
                        .contentIdentifier(ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS)
                        .filename(ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS)
                        .data("<data>Private parameter file placeholder</data>".getBytes(UTF_8))
                        .build(),
                ConfigurationPart.builder()
                        .contentIdentifier(ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS)
                        .filename(ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS)
                        .data("<data>Shared parameter file placeholder</data>".getBytes(UTF_8))
                        .build());

    }
}
