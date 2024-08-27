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
package org.niis.xroad.confclient;

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.conf.globalconf.ConfigurationClientCLI;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_PROXY;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationClientCLI.OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationClientCLI.OPTION_VERIFY_PRIVATE_PARAMS_EXISTS;
import static org.niis.xroad.confclient.ConfClientDaemonMain.APP_NAME;

@Slf4j
@UtilityClass
public class ConfClientCLIMain {
    private static final int NUM_ARGS_FROM_CONF_PROXY_FULL = 3;
    private static final int NUM_ARGS_FROM_CONF_PROXY = 2;

    static {
        SystemPropertiesLoader.create()
                .withCommonAndLocal()
                .with(CONF_FILE_PROXY, "configuration-client")
                .load();
    }

    /**
     * Main entry point of configuration client. Based on the arguments, the client will either:
     * 1) <anchor file> <configuration path> -- download and exit,
     * 2) <anchor file> -- download and verify,
     * 3) [no args] -- start as daemon.
     *
     * @param args the arguments
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        Version.outputVersionInfo(APP_NAME);
        CommandLine cmd = getCommandLine(args);
        String[] actualArgs = cmd.getArgs();

        int result = 1;
        if (actualArgs.length == NUM_ARGS_FROM_CONF_PROXY_FULL) {
            // Run configuration client in one-shot mode downloading the specified global configuration version.
            result = ConfigurationClientCLI.download(actualArgs[0], actualArgs[1], Integer.parseInt(actualArgs[2]));
        } else if (actualArgs.length == NUM_ARGS_FROM_CONF_PROXY) {
            // Run configuration client in one-shot mode downloading the current global configuration version.
            result = ConfigurationClientCLI.download(actualArgs[0], actualArgs[1]);
        } else if (actualArgs.length == 1) {
            // Run configuration client in validate mode.
            result = ConfigurationClientCLI.validate(actualArgs[0], cmd);
        }

        System.exit(result);
    }

    private static CommandLine getCommandLine(String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();

        options.addOption(OPTION_VERIFY_PRIVATE_PARAMS_EXISTS, false,
                "Verifies that configuration contains private parameters.");
        options.addOption(OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE, false,
                "Verifies that configuration contains shared parameters.");

        return parser.parse(options, args);
    }

}
