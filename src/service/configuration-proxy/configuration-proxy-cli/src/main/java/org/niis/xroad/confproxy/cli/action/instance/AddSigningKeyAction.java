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
package org.niis.xroad.confproxy.cli.action.instance;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import jakarta.inject.Singleton;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.Strings;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.confproxy.common.service.ConfProxyInstanceService;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.time.Instant;
import java.util.Date;

import static org.niis.xroad.confproxy.common.service.ConfProxyInstanceService.CONF_INI;
import static org.niis.xroad.signer.protocol.dto.KeyUsageInfo.SIGNING;

/**
 * Utility tool for adding new signing keys to a configuration proxy instance.
 */
@Singleton
public class AddSigningKeyAction extends AbstractInstanceAction {

    private static final String OPT_TOKEN_ID = "t";
    private static final String OPT_ALGORITHM_ID = "a";
    private static final String OPT_ACTIVE_KEY_ID = "ak";

    /**
     * Constructs a confproxy-add-signing-key utility program instance.
     */
    public AddSigningKeyAction(
            SignerRpcClient signerRpcClient,
            ConfigurationProxyProperties cpProperties,
            ConfProxyInstanceService confProxyInstanceService) {
        super("add-signing-key", signerRpcClient, cpProperties, confProxyInstanceService);
        getOptions()
                .addOption(PROXY_INSTANCE)
                .addOption(OPT_KEY_ID, OPT_LONG_KEY_ID, true, "Id of the key to be added")
                .addOption(OPT_TOKEN_ID, "token-id", true, "Id of the token to generate a new key")
                .addOption(OPT_ACTIVE_KEY_ID, "active-key", false, "Mark new key as active key, "
                        + "in case first key it will marked as active by default ")
                .addOption(OPT_ALGORITHM_ID, "algorithm", true, "Key algorithm used by new key (RSA/EC), default RSA");
    }

    @Override
    public final void execute(final CommandLine commandLine) {
        ensureProxyExists(commandLine);
        final ConfProxyInstance conf = loadConf(commandLine);
        if (conf == null) {
            return;
        }

        if (commandLine.hasOption(OPT_KEY_ID)) {
            String keyId = commandLine.getOptionValue(OPT_KEY_ID);
            addSigningKey(conf, keyId, commandLine.hasOption(OPT_ACTIVE_KEY_ID));
        } else if (commandLine.hasOption(OPT_TOKEN_ID)) {
            String tokenId = commandLine.getOptionValue(OPT_TOKEN_ID);
            String alg = commandLine.getOptionValue(OPT_ALGORITHM_ID, KeyAlgorithm.RSA.name());
            KeyAlgorithm keyAlgorithm = Strings.CI.equals(KeyAlgorithm.EC.name(), alg) ? KeyAlgorithm.EC : KeyAlgorithm.RSA;
            KeyInfo keyInfo = signerRpcClient.generateKey(tokenId, "key-" + System.currentTimeMillis(), keyAlgorithm);
            System.out.println("Generated key with ID " + keyInfo.getId());
            addSigningKey(conf, keyInfo.getId(), commandLine.hasOption(OPT_ACTIVE_KEY_ID));
        } else {
            printHelp();
        }
    }

    /**
     * Adds the provided signing key id to the configuration proxy properties.
     * @param conf  configuration proxy properties
     * @param keyId the key id to be added
     */
    private void addSigningKey(final ConfProxyInstance conf, final String keyId, boolean markActive) {
        final byte[] certBytes = signerRpcClient.generateSelfSignedCert(keyId, null, SIGNING, "N/A",
                new Date(0), Date.from(Instant.ofEpochSecond(Integer.MAX_VALUE)));
        var noActiveKey = conf.getActiveSigningKey() == null;
        confProxyInstanceService.addSigningKey(conf, keyId, certBytes);

        System.out.println("Saved self-signed certificate to cert_" + keyId + ".pem");
        if (noActiveKey) {
            System.out.println("No active key configured, setting new key as active in " + CONF_INI);
        }
        if (!noActiveKey && markActive) {
            confProxyInstanceService.setActiveSigningKey(conf, keyId);
        }

        System.out.println("Added key to " + CONF_INI);
    }
}
