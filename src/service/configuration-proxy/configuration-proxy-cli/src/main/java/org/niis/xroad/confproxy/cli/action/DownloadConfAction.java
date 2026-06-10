/*
 * The MIT License
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
package org.niis.xroad.confproxy.cli.action;

import jakarta.inject.Singleton;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.confproxy.common.service.ConfClientHelper;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;

@Singleton
public class DownloadConfAction extends AbstractAction {
    private static final Option ANCHOR =
            new Option("a", "anchor", true,
                    "Configuration anchor file to use for configuration download");
    private static final Option DESTINATION =
            new Option("d", "destination", true,
                    "Destination path to use for configuration download");

    private final ConfClientHelper confClientHelper;
    private final CommonProperties commonProperties;

    public DownloadConfAction(ConfClientHelper confClientHelper, CommonProperties commonProperties) {
        super("download-conf");
        this.confClientHelper = confClientHelper;
        this.commonProperties = commonProperties;

        getOptions()
                .addOption(ANCHOR)
                .addOption(DESTINATION);
    }

    @Override
    public void execute(CommandLine commandLine) {
        if (commandLine.hasOption(ANCHOR.getOpt())) {
            String anchor = commandLine.getOptionValue(ANCHOR.getOpt());
            System.out.println("Downloading configuration using anchor from: " + anchor);
            String destination = commandLine.getOptionValue(DESTINATION.getOpt());
            if (StringUtils.isBlank(destination)) {
                destination = Paths.get(commonProperties.tempFilesPath(), "conf_" + new Date().getTime()).toString();
            }
            System.out.println("Destination directory: " + anchor);
            try {
                confClientHelper.downloadConfiguration(destination, anchor);
                System.out.println("Successfully downloaded configuration to: " + destination);
            } catch (IOException e) {
                System.err.printf("Failed to downloaded configuration, error %s%n", e.getMessage());
            }

        } else {
            printHelp();
        }
    }
}
