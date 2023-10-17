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
package ee.ria.xroad.confproxy.commandline;

import ee.ria.xroad.common.conf.globalconf.ConfigurationAnchor;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.confproxy.ConfProxyProperties;
import ee.ria.xroad.confproxy.util.ConfProxyHelper;
import ee.ria.xroad.confproxy.util.OutputBuilder;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static ee.ria.xroad.confproxy.ConfProxyProperties.ACTIVE_SIGNING_KEY_ID;
import static ee.ria.xroad.confproxy.ConfProxyProperties.CONF_INI;
import static ee.ria.xroad.confproxy.ConfProxyProperties.SIGNING_KEY_ID_PREFIX;
import static ee.ria.xroad.confproxy.ConfProxyProperties.VALIDITY_INTERVAL_SECONDS;

/**
 * Utility tool for viewing the configuration proxy configuration settings.
 */
@Slf4j
public class ConfProxyUtilViewConf extends ConfProxyUtil {

    private static final String ACTIVE_KEY_NA_MSG =
            "NOT CONFIGURED (add '"
                    + ACTIVE_SIGNING_KEY_ID + "' to '" + CONF_INI + "')";
    private static final String VALIDITY_INTERVAL_NA_MSG =
            "NOT CONFIGURED (add '"
                    + VALIDITY_INTERVAL_SECONDS + "' to '" + CONF_INI + "')";

    /**
     * Constructs a confproxy-generate-anchor utility program instance.
     */
    ConfProxyUtilViewConf() {
        super("confproxy-view-conf");
        getOptions()
                .addOption(PROXY_INSTANCE)
                .addOption("a", "all", false,
                        "show configurations for all instances");
    }

    @Override
    final void execute(final CommandLine commandLine) throws Exception {
        if (commandLine.hasOption(PROXY_INSTANCE.getOpt())) {
            ensureProxyExists(commandLine);
            ConfProxyProperties conf = loadConf(commandLine);
            displayInfo(conf.getInstance(), conf);
        } else if (commandLine.hasOption("a")) {
            for (String instance : ConfProxyHelper.availableInstances()) {
                ConfProxyProperties conf;
                try {
                    conf = new ConfProxyProperties(instance);
                } catch (Exception e) {
                    fail("'" + ConfProxyProperties.CONF_INI
                            + "' could not be loaded for proxy '"
                            + instance + "': ", e);
                    continue;
                }
                displayInfo(instance, conf);
            }
        } else {
            printHelp();
        }
    }

    /**
     * Print the configuration proxy instance properties to the commandline.
     * @param instance configuration proxy instance name
     * @param conf configuration proxy properties instance
     * @throws Exception if errors occur when reading properties
     */
    private void displayInfo(final String instance,
                             final ConfProxyProperties conf) throws Exception {
        ConfigurationAnchor anchor = null;
        String anchorError = null;
        try {
            anchor = new ConfigurationAnchor(conf.getProxyAnchorPath());
        } catch (Exception e) {
            anchorError = "'" + ConfProxyProperties.ANCHOR_XML
                    + "' could not be loaded: " + e;
        }
        String delimiter = "==================================================";

        System.out.println("Configuration for proxy '" + instance + "'");
        int validityInterval = conf.getValidityIntervalSeconds();
        System.out.println("Validity interval: "
                + (validityInterval < 0 ? VALIDITY_INTERVAL_NA_MSG
                : validityInterval + " s."));
        System.out.println();

        System.out.println(ConfProxyProperties.ANCHOR_XML);
        System.out.println(delimiter);
        if (anchorError == null) {
            System.out.println("Instance identifier: "
                    + anchor.getInstanceIdentifier());
            System.out.println("Generated at:        "
                    + anchor.getGeneratedAt().toInstant());
            System.out.println("Hash:                "
                    + anchorHash(conf));
        } else {
            System.out.println(anchorError);
        }
        System.out.println();

        System.out.println("Configuration URLs");
        System.out.println(delimiter);
        var configurationProxyURLs = conf.getConfigurationProxyURLs();
        if (configurationProxyURLs.isEmpty()) {
            System.out.println("configuration-proxy.address has not been configured in 'local.ini'!");
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
                + certInfo(activeKey, conf));
        List<String> inactiveKeys = conf.getKeyList();
        if (!inactiveKeys.isEmpty()) {
            System.out.println(SIGNING_KEY_ID_PREFIX + "*:");
            inactiveKeys.forEach(k -> System.out.println("\t" + k
                    + certInfo(k, conf)));
        }
        System.out.println();
    }

    /**
     * Generates a string that describes the certificate information for the
     * provided key id.
     * @param keyId the key id
     * @param conf configuration proxy properties instance
     * @return string describing certificate information
     */
    private String certInfo(final String keyId,
                            final ConfProxyProperties conf) {
        if (keyId == null) {
            return "";
        }
        Path certPath = conf.getCertPath(keyId).toAbsolutePath();
        byte[] certBytes;
        try {
            certBytes = Files.readAllBytes(certPath);
        } catch (IOException e) {
            log.warn("Cert file missing: {}", e);
            return " (CERTIFICATE FILE MISSING!)";
        }
        try {
            CryptoUtils.readCertificate(certBytes);
        } catch (Exception e) {
            log.warn("Invalid certificate: {}", e);
            return " (INVALID CERTIFICATE - " + e.getMessage() + ")";
        }
        return " (Certificate: " + certPath.toString() + ")";
    }

    /**
     * Generates a colon delimited hex string describing the anchor file for
     * the given proxy instance.
     * @param conf configuration proxy properties instance
     * @return colon delimited hex string describing the anchor file
     * @throws Exception if the hash could not be computed
     */
    private String anchorHash(final ConfProxyProperties conf) throws Exception {
        byte[] anchorBytes = null;
        try {
            Path anchorPath = Paths.get(conf.getProxyAnchorPath());
            anchorBytes = Files.readAllBytes(anchorPath);
        } catch (IOException e) {
            fail("Failed to load proxy '" + conf.getInstance()
                    + "' anchor file: ", e);
        }
        String hash = CryptoUtils.hexDigest(CryptoUtils.SHA224_ID, anchorBytes);
        return StringUtils.join(hash.toUpperCase().split("(?<=\\G.{2})"), ':');
    }
}
