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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.confproxy.ConfProxyProperties;

import org.apache.commons.cli.CommandLine;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static ee.ria.xroad.confproxy.ConfProxyProperties.CONF_INI;

/**
 * Utility tool for creating a new configuration proxy instance
 * with default settings.
 */
public class ConfProxyUtilCreateInstance extends ConfProxyUtil {

    static final int DEFAULT_VALIDITY_INTERVAL_SECONDS = 600;

    /**
     * Constructs a confproxy-create-instance utility program instance.
     */
    ConfProxyUtilCreateInstance() {
        super("confproxy-create-instance");
        getOptions()
                .addOption(PROXY_INSTANCE);
    }

    @Override
    final void execute(final CommandLine commandLine)
            throws Exception {
        if (commandLine.hasOption(PROXY_INSTANCE.getOpt())) {
            String instance = commandLine
                    .getOptionValue(PROXY_INSTANCE.getOpt());
            System.out.println("Generating configuration directory for "
                    + "instance '" + instance + "' ...");
            String basePath = SystemProperties.getConfigurationProxyConfPath();
            Path instancePath = Paths.get(basePath, instance);
            Files.createDirectories(instancePath);
            Path confPath = instancePath.resolve(CONF_INI);
            try {
                Files.createFile(confPath);
            } catch (FileAlreadyExistsException ex) {
                fail("Configuration for instance '" + instance
                        + "' already exists, aborting. ", ex);
            }
            ConfProxyProperties conf = new ConfProxyProperties(instance);
            System.out.println("Populating '" + CONF_INI
                    + "' with default values ...");
            conf.setValidityIntervalSeconds(DEFAULT_VALIDITY_INTERVAL_SECONDS);
            System.out.println("Done.");
        } else {
            printHelp();
        }
    }
}
