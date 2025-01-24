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

import ee.ria.xroad.common.conf.globalconf.ConfigurationClientActionExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import static org.niis.xroad.confclient.core.ConfigurationClientActionExecutor.OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE;
import static org.niis.xroad.confclient.core.ConfigurationClientActionExecutor.OPTION_VERIFY_PRIVATE_PARAMS_EXISTS;

@Slf4j
@RequiredArgsConstructor
public class ConfClientCLIRunner {
    private static final int NUM_ARGS_FROM_CONF_PROXY_FULL = 3;
    private static final int NUM_ARGS_FROM_CONF_PROXY = 2;

    private final ConfigurationClientActionExecutor executor;

    /**
     * Main entry point of configuration client. Based on the arguments, the configuration client run:
     * 1) <anchor file> <configuration path> <conf version> -- in one-shot mode downloading the specified global configuration version;
     * 2) <anchor file> <configuration path> -- in one-shot mode downloading the current global configuration version;
     * 3) <anchor file> -- in validate mode.
     *
     * @param args the arguments
     * @throws Exception if an error occurs
     */
    public int run(String... args) throws Exception {
        if (args.length > 0) {
            CommandLine cmd = getCommandLine(args);
            String[] actualArgs = cmd.getArgs();

            int result;
            if (actualArgs.length == NUM_ARGS_FROM_CONF_PROXY_FULL) {
                // Run configuration client in one-shot mode downloading the specified global configuration version.
                result = executor.download(actualArgs[0], actualArgs[1], Integer.parseInt(actualArgs[2]));
            } else if (actualArgs.length == NUM_ARGS_FROM_CONF_PROXY) {
                // Run configuration client in one-shot mode downloading the current global configuration version.
                result = executor.download(actualArgs[0], actualArgs[1]);
            } else if (actualArgs.length == 1) {
                // Run configuration client in validate mode.
                result = executor.validate(actualArgs[0], cmd);
            } else {
                result = 1;
            }

            return result;
        } else {
            log.debug("No arguments given.");
        }
        return 0;
    }

    private static CommandLine getCommandLine(String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();

        options.addOption("p", OPTION_VERIFY_PRIVATE_PARAMS_EXISTS, false,
                "Verifies that configuration contains private parameters.");
        options.addOption("s", OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE, false,
                "Verifies that configuration contains shared parameters.");

        return parser.parse(options, args);
    }

}
