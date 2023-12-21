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
package ee.ria.xroad.confproxy.commandline;

import ee.ria.xroad.confproxy.ConfProxyProperties;
import ee.ria.xroad.signer.SignerProxy;

import org.apache.commons.cli.CommandLine;

import static ee.ria.xroad.confproxy.ConfProxyProperties.CONF_INI;

/**
 * Utility tool for deleting signing keys from a configuration proxy instance.
 */
public class ConfProxyUtilDelSigningKey extends ConfProxyUtil {

    /**
     * Constructs a confproxy-del-signing-key utility program instance.
     */
    ConfProxyUtilDelSigningKey() {
        super("confproxy-del-signing-key");
        getOptions()
                .addOption(PROXY_INSTANCE)
                .addOption("k", "key-id", true, "Id of the signing key to delete");
    }

    @Override
    final void execute(final CommandLine commandLine)
            throws Exception {
        ensureProxyExists(commandLine);
        final ConfProxyProperties conf = loadConf(commandLine);

        if (commandLine.hasOption("key-id")) {
            String keyId = commandLine.getOptionValue("k");
            if (keyId.equals(conf.getActiveSigningKey())) {
                fail("Not allowed to delete an active signing key!", null);
            }
            if (!conf.removeKeyId(keyId)) {
                fail("The key ID '" + keyId
                        + "' could not be found in '" + CONF_INI + "'.", null);
            }
            System.out.println("Deleted key from '" + CONF_INI + "'.");
            conf.deleteCert(keyId);
            System.out.println("Deleted self-signed certificate 'cert_"
                    + keyId + ".pem'");
            SignerProxy.deleteKey(keyId, true);
            System.out.println("Deleted key from signer");
        } else {
            printHelp();
        }
    }
}
