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

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.niis.xroad.bootstrap.XrdQuarkusApplication;
import org.niis.xroad.confclient.config.ConfigurationClientProperties;

@QuarkusMain
@Slf4j
public class ConfClientMain {

    public static void main(String[] args) {
        if (args.length > 0) {
            // run cli.
            // todo consider using Quarkus for CLI
            int result = runCli(args);
            System.exit(result);
        } else {
            Quarkus.run(XrdQuarkusApplication.class, args);
        }
    }

    private static final int ERROR_CODE_INTERNAL = 125;

    public static int runCli(String... args) {
        ConfigurationClientProperties config = new ConfigurationClientProperties() {
            @Override
            public int updateInterval() {
                // not used in CLI
                return 0;
            }

            @Override
            public String proxyConfigurationBackupCron() {
                // not used in CLI
                return "";
            }

            @Override
            public String configurationAnchorFile() {
                return ConfigProvider.getConfig().getConfigValue("xroad.configuration-client.configuration-anchor-file").getValue();
            }
        };

        ConfClientCLIRunner confClientCLIRunner = new ConfClientCLIRunner(new ConfigurationClientActionExecutor(config));
        try {
            return confClientCLIRunner.run(args);
        } catch (Exception e) {
            log.error("Failed to run Configuration Client CLI command", e);
            return ERROR_CODE_INTERNAL;
        }
    }

}
