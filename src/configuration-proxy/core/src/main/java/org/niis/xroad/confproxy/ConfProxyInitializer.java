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

package org.niis.xroad.confproxy;

import ee.ria.xroad.signer.SignerRpcClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confproxy.commandline.ConfProxyUtilAddSigningKey;
import org.niis.xroad.confproxy.commandline.ConfProxyUtilCreateInstance;
import org.niis.xroad.confproxy.config.ConfProxyProperties;
import org.niis.xroad.confproxy.util.ConfProxyHelper;
import org.springframework.beans.factory.InitializingBean;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.niis.xroad.confproxy.commandline.ConfProxyUtilCreateInstance.DEFAULT_VALIDITY_INTERVAL_SECONDS;

@RequiredArgsConstructor
@Slf4j
public class ConfProxyInitializer implements InitializingBean {

    private final SignerRpcClient signerRpcClient;
    private final ConfProxyProperties confProxyProperties;


    @Override
    public void afterPropertiesSet() throws Exception {

        // token must be active
//        signerRpcClient.initSoftwareToken("123456".toCharArray());
//        signerRpcClient.activateToken(SignerRpcClient.SSL_TOKEN_ID, "123456".toCharArray());

        Set<String> existingInstances = currentInstances();

        for (Map.Entry<String, ConfProxyProperties.InstanceProperties> instanceEntry : confProxyProperties.instances().entrySet()) {
            if (existingInstances.contains(instanceEntry.getKey())) {
                log.info("Configuration Proxy for {} already exists, skipping initial config", instanceEntry.getKey());
                continue;
            }
            try {
                String instance = instanceEntry.getKey();
                ConfProxyProperties.InstanceProperties instanceProperties = instanceEntry.getValue();

                ConfProxyUtilCreateInstance confProxyUtilCreateInstance = new ConfProxyUtilCreateInstance(confProxyProperties, signerRpcClient);
                long validityInterval = instanceProperties.validityInterval() != null
                        ? instanceProperties.validityInterval().toSeconds()
                        : DEFAULT_VALIDITY_INTERVAL_SECONDS;
                confProxyUtilCreateInstance.execute(instance,validityInterval);

                ConfProxyUtilAddSigningKey confProxyUtilAddSigningKey = new ConfProxyUtilAddSigningKey(confProxyProperties, signerRpcClient);
                confProxyUtilAddSigningKey.execute(instance, instanceProperties.signingKeyId(), instanceProperties.tokenId());

                if (instanceProperties.sourceAnchorFileUri() != null) {
                    copyAnchorFile(instance, instanceProperties.sourceAnchorFileUri());
                }

                existingInstances.remove(instance);

            } catch (Exception e) {
                log.error("Failed to initialize Configuration Proxy for {}", instanceEntry.getKey(), e);
            }
        }

        // remove instances which do not exist in configuration anymore?
    }

    private void copyAnchorFile(String instance, URI sourceAnchorFileUri) {
        try {
            // add support for http://.. ?
            Files.copy(Paths.get(sourceAnchorFileUri),
                    Paths.get(confProxyProperties.configurationPath(), instance, "anchor.xml"));
        } catch (Exception e) {
            log.error("Failed to copy anchor file for instance {}", instance, e);
        }
    }

    private Set<String> currentInstances() throws Exception {
        return new HashSet<>(ConfProxyHelper.availableInstances(confProxyProperties.configurationPath()));
    }

}
