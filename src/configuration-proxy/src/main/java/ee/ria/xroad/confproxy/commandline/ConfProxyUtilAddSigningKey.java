/**
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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.confproxy.ConfProxyProperties;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.message.GenerateKey;
import ee.ria.xroad.signer.protocol.message.GenerateSelfSignedCert;
import ee.ria.xroad.signer.protocol.message.GenerateSelfSignedCertResponse;

import org.apache.commons.cli.CommandLine;

import java.util.Date;

import static ee.ria.xroad.confproxy.ConfProxyProperties.CONF_INI;

/**
 * Utility tool for adding new signing keys to a configuration proxy instance.
 */
public class ConfProxyUtilAddSigningKey extends ConfProxyUtil {

    /**
     * Constructs a confproxy-add-signing-key utility program instance.
     */
    ConfProxyUtilAddSigningKey() {
        super("confproxy-add-signing-key");
        getOptions()
                .addOption(PROXY_INSTANCE)
                .addOption("k", "key-id", true, "Id of the key to be added")
                .addOption("t", "token-id", true,
                        "Id of the token to generate a new key");
    }

    @Override
    final void execute(final CommandLine commandLine)
            throws Exception {
        ensureProxyExists(commandLine);
        final ConfProxyProperties conf = loadConf(commandLine);

        if (commandLine.hasOption("key-id")) {
            String keyId = commandLine.getOptionValue("k");
            addSigningKey(conf, keyId);
        } else if (commandLine.hasOption("token-id")) {
            String tokenId = commandLine.getOptionValue("t");
            KeyInfo keyInfo = SignerClient.execute(
                    new GenerateKey(tokenId, "key-" + System.currentTimeMillis())
            );
            System.out.println("Generated key with ID " + keyInfo.getId());
            addSigningKey(conf, keyInfo.getId());
        } else {
            printHelp();
        }
    }

    /**
     * Adds the provided signing key id to the configuration proxy properties.
     * @param conf configuration proxy properties
     * @param keyId the key id to be added
     * @throws Exception if signer responds with an error or the properties
     * file cannot be accessed
     */
    private void addSigningKey(final ConfProxyProperties conf,
                               final String keyId) throws Exception {
        ClientId.Conf clientId = null;
        GenerateSelfSignedCertResponse response = SignerClient.execute(
                new GenerateSelfSignedCert(keyId, "N/A",
                        new Date(0), new Date(Integer.MAX_VALUE),
                        KeyUsageInfo.SIGNING, clientId));
        byte[] certBytes = response.getCertificateBytes();
        conf.saveCert(keyId, certBytes);
        System.out.println("Saved self-signed certificate to cert_"
                + keyId + ".pem");
        if (conf.getActiveSigningKey() == null) {
            System.out.println("No active key configured,"
                    + " setting new key as active in " + CONF_INI);
            conf.setActiveSigningKey(keyId);
        }
        conf.addKeyId(keyId);
        System.out.println("Added key to " + CONF_INI);
    }
}
