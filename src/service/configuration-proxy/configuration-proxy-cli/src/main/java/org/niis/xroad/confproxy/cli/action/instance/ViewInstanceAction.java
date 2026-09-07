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
package org.niis.xroad.confproxy.cli.action.instance;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.confproxy.common.service.ConfProxyInstanceService;
import org.niis.xroad.confproxy.common.service.OutputBuilder;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.util.List;

import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.ACTIVE_SIGNING_KEY_ID;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.ANCHOR_XML;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.SIGNING_KEY_ID_PREFIX;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.VALIDITY_INTERVAL_SECONDS;
import static org.niis.xroad.confproxy.common.service.ConfProxyInstanceService.CONF_INI;
import static org.niis.xroad.confproxy.common.utils.ConfProxyUtils.getConfigurationProxyURLs;

/**
 * Utility tool for viewing the configuration proxy configuration settings.
 */
@Slf4j
@Singleton
public class ViewInstanceAction extends AbstractInstanceAction {

    private static final String ACTIVE_KEY_NA_MSG =
            "NOT CONFIGURED (add '"
                    + ACTIVE_SIGNING_KEY_ID + "' to '" + CONF_INI + "')";
    private static final String VALIDITY_INTERVAL_NA_MSG =
            "NOT CONFIGURED (add '"
                    + VALIDITY_INTERVAL_SECONDS + "' to '" + CONF_INI + "')";

    /**
     * Constructs a confproxy-generate-anchor utility program instance.
     */
    public ViewInstanceAction(
            SignerRpcClient signerRpcClient,
            ConfigurationProxyProperties cpProperties,
            ConfProxyInstanceService confProxyInstanceService) {
        super("view-conf", signerRpcClient, cpProperties, confProxyInstanceService);
        getOptions()
                .addOption(PROXY_INSTANCE)
                .addOption("a", "all", false,
                        "show configurations for all instances");
    }

    @Override
    public final void execute(final CommandLine commandLine) {
        if (commandLine.hasOption(PROXY_INSTANCE.getOpt())) {
            ensureProxyExists(commandLine);
            ConfProxyInstance conf = loadConf(commandLine);
            if (conf == null) {
                return;
            }

            displayInfo(conf);
        } else if (commandLine.hasOption("a")) {
            for (String instance : confProxyInstanceService.availableInstancesNames()) {
                try {
                    ConfProxyInstance conf = confProxyInstanceService.loadInstance(instance);
                    displayInfo(conf);
                } catch (Exception e) {
                    fail("'" + CONF_INI + "' could not be loaded for proxy '" + instance + "': ", e);
                }
            }
        } else {
            printHelp();
        }
    }

    /**
     * Print the configuration proxy instance properties to the commandline.
     * @param conf configuration proxy properties instance
     */
    private void displayInfo(final ConfProxyInstance conf) {
        ConfProxyInstanceService.AnchorAndHash anchor = null;
        String anchorError = null;
        try {
            anchor = confProxyInstanceService.anchorHash(conf);
        } catch (Exception e) {
            anchorError = "'" + ANCHOR_XML
                    + "' could not be loaded: " + e;
        }
        String delimiter = "==================================================";

        System.out.println("Configuration for proxy '" + conf.getInstance() + "'");
        int validityInterval = conf.getValidityIntervalSeconds();
        System.out.println("Validity interval: "
                + (validityInterval < 0 ? VALIDITY_INTERVAL_NA_MSG
                : validityInterval + " s."));
        System.out.println();

        System.out.println(ANCHOR_XML);
        System.out.println(delimiter);
        if (anchorError == null) {
            System.out.println("Instance identifier: " + anchor.anchor().getInstanceIdentifier());
            System.out.println("Generated at:        " + anchor.anchor().getGeneratedAt().toInstant());
            System.out.println("Hash:                " + anchor.hash());
        } else {
            System.out.println(anchorError);
        }
        System.out.println();

        System.out.println("Configuration URLs");
        System.out.println(delimiter);
        var configurationProxyURLs = getConfigurationProxyURLs(cpProperties.address(), conf.getInstance());
        if (configurationProxyURLs.isEmpty()) {
            System.out.println("xroad.configuration-proxy.address has not been configured in 'local.yaml'!");
        } else {
            for (String proxyURL : configurationProxyURLs) {
                System.out.println(proxyURL + "/" + OutputBuilder.SIGNED_DIRECTORY_NAME);
            }
        }
        System.out.println();

        System.out.println("Signing keys and certificates");
        System.out.println(delimiter);

        System.out.println(ACTIVE_SIGNING_KEY_ID + ":");
        String activeKey = conf.getActiveSigningKey();
        System.out.println("\t"
                + (activeKey == null ? ACTIVE_KEY_NA_MSG : activeKey)
                + confProxyInstanceService.certInfo(conf, activeKey).map(info -> " " + info).orElse(""));
        List<String> inactiveKeys = conf.getKeyList();
        if (!inactiveKeys.isEmpty()) {
            System.out.println(SIGNING_KEY_ID_PREFIX + "*:");
            inactiveKeys.forEach(k -> System.out.println("\t" + k + confProxyInstanceService.certInfo(conf, k)
                    .map(info -> " " + info)
                    .orElse("")));
        }
        System.out.println();
    }


}
