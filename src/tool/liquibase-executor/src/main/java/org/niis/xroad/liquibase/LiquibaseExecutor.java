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

/**
 * Entry point wrapper for Liquibase CLI that disables analytics
 * before any Liquibase class initialization and delegates all
 * command processing to {@link LiquibaseCommandLine}.
 */
public final class LiquibaseExecutor {

    private static final String VERSION = "X-Road Liquibase Executor 1.0";

    private LiquibaseExecutor() {
        // utility class
    }

    /**
     * Main entry point. Disables Liquibase analytics, then delegates to LiquibaseCommandLine.
     *
     * @param args CLI arguments passed through to Liquibase
     */
    public static void main(String[] args) {
        System.setProperty("liquibase.analytics.enabled", "false");

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
}
