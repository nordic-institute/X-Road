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

/**
 * Entry point wrapper for Liquibase CLI that configures logging and disables analytics
 * before any Liquibase class initialization, then delegates all command processing
 * to {@link LiquibaseCommandLine}.
 *
 * <p>Logging setup:
 * <ul>
 *   <li>Sets {@code xroad.liquibase.schema} system property from {@code --defaultSchemaName}
 *       CLI argument, used by logback.xml for schema-specific log file paths.</li>
 *   <li>Installs JUL-to-SLF4J bridge so that Liquibase 5.x internal logging (which uses
 *       {@code java.util.logging}) is routed through SLF4J/Logback.</li>
 * </ul>
 */
public final class LiquibaseExecutor {

    private static final String VERSION = "X-Road Liquibase Executor 1.0";

    private LiquibaseExecutor() {
        // utility class
    }

    /**
     * Main entry point. Sets up schema-specific logging, installs JUL-to-SLF4J bridge,
     * disables Liquibase analytics, then delegates to LiquibaseCommandLine.
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

        // 4. Handle --version or delegate to Liquibase CLI
        if (args.length == 1 && "--version".equals(args[0])) {
            System.out.println(VERSION);
            LiquibaseCommandLine cli = new LiquibaseCommandLine();
            cli.execute(new String[]{"--version"});
            return;
        }

        LiquibaseCommandLine cli = new LiquibaseCommandLine();
        int exitCode = cli.execute(args);
        System.exit(exitCode);
    }

    /**
     * Extracts the value of a named CLI argument.
     * Supports both {@code --arg=value} and {@code --arg value} formats.
     *
     * @param args    the CLI arguments array
     * @param argName the argument name (e.g., {@code "--defaultSchemaName"})
     * @return the argument value, or {@code null} if not found
     */
    private static String extractArg(String[] args, String argName) {
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
