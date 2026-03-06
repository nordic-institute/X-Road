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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Entry point wrapper for Liquibase CLI that uses picocli for argument parsing,
 * configures logging via slf4j, disables analytics, and translates X-Road-specific
 * flags before delegating to {@link LiquibaseCommandLine}.
 *
 * <p>All accepted arguments are explicitly declared as picocli {@link Option} fields.
 * Unknown options are rejected by picocli automatically.
 */
@Command(
        name = "liquibase-executor",
        version = "X-Road Liquibase Executor 1.0",
        mixinStandardHelpOptions = true,
        description = "X-Road Liquibase migration executor"
)
public class LiquibaseExecutor implements Callable<Integer> {

    @Option(names = "--changelog", required = true,
            description = "Database changelog name (serverconf, centerui, messagelog, op-monitor)")
    String changelog;

    @Option(names = "--url", required = true,
            description = "JDBC database URL")
    String url;

    @Option(names = "--username",
            description = "Database username")
    String username;

    @Option(names = "--password",
            description = "Database password")
    String password;

    @Option(names = "--defaultSchemaName",
            description = "Default database schema name (also auto-derives -Ddb_schema)")
    String defaultSchemaName;

    @Option(names = "--contexts",
            description = "Liquibase contexts to run")
    String contexts;

    @Option(names = "--prop-db-user",
            description = "Runtime DB user for GRANT statements (translates to -Ddb_user)")
    String propDbUser;

    @Option(names = "--prop-proxy-ui-superuser",
            description = "Docker-only: proxy UI superuser (translates to -Dproxy_ui_superuser)")
    String propProxyUiSuperuser;

    @Option(names = "--prop-proxy-ui-superuser-password",
            description = "Docker-only: proxy UI superuser password (translates to -Dproxy_ui_superuser_password)")
    String propProxyUiSuperuserPassword;

    @Parameters(index = "0", description = "Liquibase command (e.g., update)")
    String command;

    /**
     * Main entry point. Sets up system properties before picocli parsing,
     * then delegates to picocli for execution.
     *
     * @param args CLI arguments
     */
    public static void main(String[] args) {
        initSystemProperties(args);
        int exitCode = new CommandLine(new LiquibaseExecutor()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Lightweight pre-scan of raw args to set system properties that must be
     * configured before any SLF4J logger initialization or Liquibase class loading.
     *
     * @param args raw CLI arguments
     */
    static void initSystemProperties(String[] args) {
        // Extract --defaultSchemaName for logback file path (supports both = and space formats)
        String schema = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--defaultSchemaName=")) {
                schema = args[i].substring("--defaultSchemaName=".length());
                break;
            }
            if ("--defaultSchemaName".equals(args[i]) && i + 1 < args.length) {
                schema = args[i + 1];
                break;
            }
        }
        System.setProperty("xroad.liquibase.schema", schema != null ? schema : "unknown");

        // Disable analytics before any Liquibase class initialization
        System.setProperty("liquibase.analytics.enabled", "false");

        // Bridge java.util.logging (used internally by Liquibase 5.x) to SLF4J/Logback
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    @Override
    public Integer call() {
        // Logger created here (not as static field) to ensure xroad.liquibase.schema
        // system property is set before logback reads it for file path
        var logger = LoggerFactory.getLogger(LiquibaseExecutor.class);

        String[] liquibaseArgs = buildLiquibaseArgs();

        logger.info("Executing Liquibase: {}", String.join(" ", liquibaseArgs));

        LiquibaseCommandLine cli = new LiquibaseCommandLine();
        int exitCode = cli.execute(liquibaseArgs);

        if (exitCode == 0) {
            logger.info("Liquibase completed successfully");
        } else {
            logger.error("Liquibase failed with exit code {}", exitCode);
        }

        return exitCode;
    }

    /**
     * Builds the Liquibase-native argument array from picocli-parsed fields.
     * -D flags are placed after the command word as required by Liquibase's picocli subcommand parsing.
     *
     * @return the translated argument array for LiquibaseCommandLine
     */
    String[] buildLiquibaseArgs() {
        List<String> args = new ArrayList<>();
        List<String> dFlags = new ArrayList<>();

        // Translate --changelog to --changeLogFile
        args.add("--changeLogFile=liquibase/" + changelog + "-changelog.xml");

        // Pass through standard Liquibase options
        args.add("--url=" + url);
        if (username != null) {
            args.add("--username=" + username);
        }
        if (password != null) {
            args.add("--password=" + password);
        }
        if (defaultSchemaName != null) {
            args.add("--defaultSchemaName=" + defaultSchemaName);
        }
        if (contexts != null) {
            args.add("--contexts=" + contexts);
        }

        // Command word
        args.add(command);

        // -D flags after command word
        if (propDbUser != null) {
            dFlags.add("-Ddb_user=" + propDbUser);
        }
        if (propProxyUiSuperuser != null) {
            dFlags.add("-Dproxy_ui_superuser=" + propProxyUiSuperuser);
        }
        if (propProxyUiSuperuserPassword != null) {
            dFlags.add("-Dproxy_ui_superuser_password=" + propProxyUiSuperuserPassword);
        }
        // Auto-derive -Ddb_schema from --defaultSchemaName
        if (defaultSchemaName != null) {
            dFlags.add("-Ddb_schema=" + defaultSchemaName);
        }

        args.addAll(dFlags);
        return args.toArray(new String[0]);
    }
}
