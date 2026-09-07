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
package org.niis.xroad.confproxy.cli.action.apikey;

import jakarta.inject.Singleton;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.niis.xroad.confproxy.jpa.service.ApiKeyService;

@Singleton
public class RevokeApiKeyAction extends AbstractApiKeyAction {
    private static final Option ID = Option.builder()
            .option("i")
            .longOpt("id")
            .hasArg()
            .desc("ID of the API key to revoke")
            .get();


    public RevokeApiKeyAction(ApiKeyService apiKeyService) {
        super("revoke-api-key", apiKeyService);
        getOptions()
                .addOption(ID);
    }

    @Override
    public void execute(CommandLine commandLine) {
        if (commandLine.hasOption(ID.getOpt())) {
            var idStr = commandLine.getOptionValue(ID.getOpt());

            long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                System.err.println("Invalid ID: '" + idStr + "'. Only numbers are allowed.");
                return;
            }

            if (apiKeyService.revoke(id)) {
                System.out.println("API key revoked.");
            } else {
                System.out.println("API key with ID " + idStr + " not found.");
            }
        } else {
            printHelp();
        }
    }
}
