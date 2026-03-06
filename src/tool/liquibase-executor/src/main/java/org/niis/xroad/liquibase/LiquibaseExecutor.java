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
package org.niis.xroad.liquibase;

import liquibase.integration.commandline.LiquibaseCommandLine;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Entry point wrapper for Liquibase CLI that configures logging, disables analytics,
 * translates X-Road-specific flags, and validates required arguments before delegating
 * all command processing to {@link LiquibaseCommandLine}.
 *
 * <p>X-Road-specific flags:
 * <ul>
 *   <li>{@code --changelog=<name>} - translates to {@code --changeLogFile=liquibase/<name>-changelog.xml}</li>
 *   <li>{@code --prop-db-user=<val>} - translates to {@code -Ddb_user=<val>}</li>
 *   <li>{@code --prop-proxy-ui-superuser=<val>} - translates to {@code -Dproxy_ui_superuser=<val>}</li>
 *   <li>{@code --prop-proxy-ui-superuser-password=<val>} - translates to {@code -Dproxy_ui_superuser_password=<val>}</li>
 * </ul>
 *
 * <p>{@code -Ddb_schema} is auto-derived from {@code --defaultSchemaName} value.
 * Raw {@code -D} flags are rejected; use {@code --prop-*} flags instead.
 */
public final class LiquibaseExecutor {

    private static final String VERSION = "X-Road Liquibase Executor 1.0";

    /**
     * Known --prop-* flag names mapped to their Liquibase -D property names.
     * Hyphenated flag names are translated to underscored property names.
     */
    static final Map<String, String> KNOWN_PROPS = Map.of(
            "db-user", "db_user",
            "db-schema", "db_schema",
            "proxy-ui-superuser", "proxy_ui_superuser",
            "proxy-ui-superuser-password", "proxy_ui_superuser_password"
    );

    private LiquibaseExecutor() {
        // utility class
    }

    /**
     * Main entry point. Sets up schema-specific logging, installs JUL-to-SLF4J bridge,
     * disables Liquibase analytics, translates args, validates, then delegates to LiquibaseCommandLine.
     *
     * @param args CLI arguments passed through to Liquibase
     */
    public static void main(String[] args) {
        // 1. Set schema name for logback file path BEFORE any SLF4J logger init
        String schema = extractArg(args, "--defaultSchemaName");
        System.setProperty("xroad.liquibase.schema", schema != null ? schema : "unknown");

        // 2. Disable analytics before any Liquibase class initialization
        System.setProperty("liquibase.analytics.enabled", "false");

        // 3. Bridge java.util.logging (used internally by Liquibase 5.x) to SLF4J/Logback
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        try {
            // 4. Translate X-Road-specific args and validate
            args = translateArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
            return;
        }

        // 5. Handle --help: print X-Road help then delegate to Liquibase
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.print(getHelpText());
            LiquibaseCommandLine cli = new LiquibaseCommandLine();
            cli.execute(new String[]{"--help"});
            return;
        }

        // 6. Handle --version
        if (args.length == 1 && "--version".equals(args[0])) {
            System.out.println(VERSION);
            LiquibaseCommandLine cli = new LiquibaseCommandLine();
            cli.execute(new String[]{"--version"});
            return;
        }

        // 7. Delegate to Liquibase CLI
        LiquibaseCommandLine cli = new LiquibaseCommandLine();
        int exitCode = cli.execute(args);
        System.exit(exitCode);
    }

    /**
     * Translates X-Road-specific arguments to Liquibase-native arguments.
     * Performs a single pass translating --changelog, --prop-*, rejecting raw -D and old --schema,
     * then auto-derives -Ddb_schema from --defaultSchemaName.
     *
     * <p>For --help and --version, bypasses all validation and returns args unchanged.
     *
     * @param args the CLI arguments array
     * @return a new array with X-Road args translated to Liquibase-native args
     * @throws IllegalArgumentException if validation fails (unknown prop, raw -D, missing required args, old --schema)
     */
    static String[] translateArgs(String[] args) {
        // Bypass validation for --help and --version
        for (String arg : args) {
            if ("--help".equals(arg) || "--version".equals(arg)) {
                return args;
            }
        }

        List<String> result = new ArrayList<>();
        List<String> dFlags = new ArrayList<>();
        String defaultSchemaName = extractArg(args, "--defaultSchemaName");
        boolean hasChangelog = false;
        boolean hasUrl = false;
        boolean hasExplicitDbSchema = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.startsWith("--changelog")) {
                // Translate --changelog=X to --changeLogFile=liquibase/X-changelog.xml
                String value;
                if (arg.contains("=")) {
                    value = arg.substring("--changelog=".length());
                } else if (i + 1 < args.length) {
                    value = args[++i];
                } else {
                    throw new IllegalArgumentException("--changelog requires a value");
                }
                result.add("--changeLogFile=liquibase/" + value + "-changelog.xml");
                hasChangelog = true;

            } else if (arg.startsWith("--prop-")) {
                // Translate --prop-X=V to -DX=V with known-set validation
                String propNameAndValue;
                if (arg.contains("=")) {
                    propNameAndValue = arg.substring("--prop-".length());
                } else if (i + 1 < args.length) {
                    String propName = arg.substring("--prop-".length());
                    propNameAndValue = propName + "=" + args[++i];
                } else {
                    throw new IllegalArgumentException(arg + " requires a value");
                }

                int eqIdx = propNameAndValue.indexOf('=');
                String propName = propNameAndValue.substring(0, eqIdx);
                String propValue = propNameAndValue.substring(eqIdx + 1);

                String liquibaseProp = KNOWN_PROPS.get(propName);
                if (liquibaseProp == null) {
                    throw new IllegalArgumentException("Unknown property '--prop-" + propName
                            + "'. Known properties: " + String.join(", ",
                            KNOWN_PROPS.keySet().stream().sorted().toList()));
                }

                if ("db_schema".equals(liquibaseProp)) {
                    hasExplicitDbSchema = true;
                }
                dFlags.add("-D" + liquibaseProp + "=" + propValue);

            } else if (arg.startsWith("-D")) {
                // Reject raw -D flags
                throw new IllegalArgumentException("Raw -D flags are not accepted. Use --prop-* instead: " + arg);

            } else if (arg.startsWith("--schema")) {
                // Reject old --schema flag
                throw new IllegalArgumentException("--schema is no longer accepted. Use --changelog instead.");

            } else {
                // Pass through everything else (--url, --password, --username, --defaultSchemaName, --contexts, update, etc.)
                result.add(arg);
                if (arg.startsWith("--url")) {
                    hasUrl = true;
                }
                if (arg.startsWith("--changeLogFile")) {
                    hasChangelog = true;
                }
            }
        }

        // Auto-derive -Ddb_schema from --defaultSchemaName (unless explicitly provided via --prop-db-schema)
        if (defaultSchemaName != null && !hasExplicitDbSchema) {
            dFlags.add("-Ddb_schema=" + defaultSchemaName);
        }

        // Append all -D flags at the end (after command word) as required by picocli subcommand parsing
        result.addAll(dFlags);

        // Validate required args
        if (!hasChangelog) {
            throw new IllegalArgumentException("--changelog is required. "
                    + "Usage: liquibase.sh --changelog=<name> --url=<jdbc-url> [options] update");
        }
        if (!hasUrl) {
            throw new IllegalArgumentException("--url is required. "
                    + "Usage: liquibase.sh --changelog=<name> --url=<jdbc-url> [options] update");
        }

        return result.toArray(new String[0]);
    }

    /**
     * Returns X-Road-specific help text describing the executor's custom flags.
     *
     * @return the help text string
     */
    static String getHelpText() {
        return """
                X-Road Liquibase Executor

                X-Road-specific flags (processed before Liquibase CLI):
                  --changelog=<name>                        Database changelog name (serverconf, centerui, messagelog, op-monitor)
                                                            Translates to --changeLogFile=liquibase/<name>-changelog.xml
                  --prop-db-user=<user>                     Runtime DB user for GRANT statements (-Ddb_user)
                  --prop-proxy-ui-superuser=<user>           Docker-only: proxy UI superuser (-Dproxy_ui_superuser)
                  --prop-proxy-ui-superuser-password=<pw>    Docker-only: proxy UI superuser password (-Dproxy_ui_superuser_password)
                  --help                                    Show this help and Liquibase help
                  --version                                 Show X-Road executor and Liquibase versions

                Note: -Ddb_schema is auto-derived from --defaultSchemaName
                Note: Raw -D flags are not accepted; use --prop-* flags instead

                --- Liquibase CLI help follows ---

                """;
    }

    /**
     * Extracts the value of a named CLI argument.
     * Supports both {@code --arg=value} and {@code --arg value} formats.
     *
     * @param args    the CLI arguments array
     * @param argName the argument name (e.g., {@code "--defaultSchemaName"})
     * @return the argument value, or {@code null} if not found
     */
    static String extractArg(String[] args, String argName) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith(argName + "=")) {
                return args[i].substring(argName.length() + 1);
            }
            if (args[i].equals(argName) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }
}
