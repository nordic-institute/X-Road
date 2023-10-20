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

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.signer.protocol.RpcSignerClient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_CONFPROXY;

/**
 * Main program for launching configuration proxy utility tools.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfProxyUtilMain {

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
                .with(CONF_FILE_CONFPROXY, "configuration-proxy")
                .load();
    }

    private static CommandLineParser cmdLineParser;

    /**
     * Configuration proxy utility tool program entry point.
     *
     * @param args program args
     */
    public static void main(final String[] args) {
        try {
            setup();
            runUtilWithArgs(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            log.error("Error while running confproxy util:", e);
        } finally {
            RpcSignerClient.shutdown();
        }
    }

    /**
     * Initialize configuration proxy utility program components.
     */
    static void setup() throws Exception {
        RpcSignerClient.init();

        cmdLineParser = new DefaultParser();
    }

    /**
     * Executes the utility program with the provided argument list.
     *
     * @param args program arguments
     * @throws Exception if any errors occur during execution
     */
    static void runUtilWithArgs(final String[] args) throws Exception {
        String utilClass = args[0];
        ConfProxyUtil util = createUtilInstance(utilClass);

        Options opts = util.getOptions();
        CommandLine commandLine = cmdLineParser.parse(opts, args);
        util.execute(commandLine);
    }

    /**
     * Creates an utility program instance of the provided class name.
     *
     * @param className name of the utility program class
     * @return an instance of the requested utility program
     * @throws Exception if class could not be found or an instance could
     *                   not be created
     */
    @SuppressWarnings("unchecked")
    static ConfProxyUtil createUtilInstance(final String className) throws Exception {
        Class<ConfProxyUtil> utilClass =
                (Class<ConfProxyUtil>) Class.forName(className);
        return utilClass.newInstance();
    }
}
