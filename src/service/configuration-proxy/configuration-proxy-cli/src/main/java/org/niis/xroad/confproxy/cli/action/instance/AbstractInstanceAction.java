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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.niis.xroad.confproxy.cli.ExitException;
import org.niis.xroad.confproxy.cli.action.AbstractAction;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.confproxy.common.service.ConfProxyInstanceService;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.io.File;
import java.nio.file.Paths;

/**
 * Base for all the configuration proxy utility tools.
 */

public abstract class AbstractInstanceAction extends AbstractAction {

    protected final SignerRpcClient signerRpcClient;
    protected final ConfigurationProxyProperties cpProperties;
    protected final ConfProxyInstanceService confProxyInstanceService;

    protected static final String OPT_KEY_ID = "k";
    protected static final String OPT_LONG_KEY_ID = "key-id";

    protected static final Option PROXY_INSTANCE =
            new Option("p", "proxy-instance", true,
                    "configuration-client proxy instance code");

    public AbstractInstanceAction(String name,
                                  SignerRpcClient signerRpcClient,
                                  ConfigurationProxyProperties cpProperties,
                                  ConfProxyInstanceService confProxyInstanceService) {
        super(name);
        this.signerRpcClient = signerRpcClient;
        this.cpProperties = cpProperties;
        this.confProxyInstanceService = confProxyInstanceService;
    }

    /**
     * Loads configuration proxy properties based on the instance provided
     * through the commandline.
     * @param commandLine holds arguments for the utility program
     * @return configuration proxy properties instance
     */
    protected ConfProxyInstance loadConf(final CommandLine commandLine) {
        if (commandLine.hasOption(PROXY_INSTANCE.getLongOpt())) {
            String instance = commandLine.getOptionValue(PROXY_INSTANCE.getOpt());

            return confProxyInstanceService.loadInstance(instance);
        } else {
            printHelp();
        }
        return null;
    }

    /**
     * Makes sure the configuration proxy instance that is requested from the
     * commandline exists.
     * @param commandLine holds arguments for the utility program
     */
    protected void ensureProxyExists(final CommandLine commandLine) {
        if (commandLine.hasOption(PROXY_INSTANCE.getLongOpt())) {
            String instance = commandLine.getOptionValue(PROXY_INSTANCE.getOpt());
            File instanceDir = Paths.get(cpProperties.configurationPath(), instance).toFile();
            if (!instanceDir.exists()) {
                fail("Configuration for proxy instance '" + instance + "' does not exist.", null);
            }
        }
    }

    /**
     * Abort the configuration proxy utility program with the provided message.
     * @param msg the error message to display
     */
    protected void fail(final String msg, final Exception e) {
        if (e == null) {
            throw new ExitException(msg);
        }
        throw new ExitException(msg, e);
    }
}
