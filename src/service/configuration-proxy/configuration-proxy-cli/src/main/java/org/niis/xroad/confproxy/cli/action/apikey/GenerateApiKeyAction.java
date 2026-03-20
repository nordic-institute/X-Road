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
import org.niis.xroad.confproxy.jpa.domain.Role;
import org.niis.xroad.confproxy.jpa.service.ApiKeyService;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class GenerateApiKeyAction extends AbstractApiKeyAction {
    private static final String API_KEY_TPL = """
            API key: %s
                ID: %d
                Roles: %s
            """;

    private static final Option ROLES = Option.builder()
            .option("r")
            .longOpt("roles")
            .desc("Roles to associate with new API key")
            .hasArgs()
            .get();

    private static final String ROLE_PREFIX = "XROAD_";

    private static final Set<String> ALLOWED_ROLES = Arrays.stream(Role.values())
            .map(Enum::name)
            .map(name -> name.substring(ROLE_PREFIX.length()))
            .collect(Collectors.toSet());

    public GenerateApiKeyAction(ApiKeyService apiKeyService) {
        super("generate-api-key", apiKeyService);
        getOptions()
                .addOption(ROLES);
    }

    @Override
    public void execute(CommandLine commandLine) {
        if (commandLine.hasOption(ROLES.getOpt())) {
            var roles = commandLine.getOptionValues(ROLES.getOpt());
            var roleSet = Arrays.stream(roles)
                    .map(String::trim)
                    .collect(Collectors.toSet());

            for (var role : roleSet) {
                if (!ALLOWED_ROLES.contains(role)) {
                    System.out.printf("Unknown role: '%s' provided. Allowed roles are: %s%n", role, String.join(", ", ALLOWED_ROLES));
                    return;
                }
            }

            var roleEnums = roleSet.stream()
                    .map(String::toUpperCase)
                    .map(role -> ROLE_PREFIX + role)
                    .map(Role::valueOf)
                    .collect(Collectors.toSet());

            var newKey = apiKeyService.createNew(roleEnums);
            System.out.println("New API key was created.");
            System.out.printf((API_KEY_TPL) + "%n", newKey.code(), newKey.key().id(), newKey.key().roles().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", ")));

        } else {
            printHelp();
        }
    }
}
