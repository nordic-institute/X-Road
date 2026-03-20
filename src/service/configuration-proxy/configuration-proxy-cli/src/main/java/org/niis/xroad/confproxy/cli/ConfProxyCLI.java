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
package org.niis.xroad.confproxy.cli;

import ee.ria.xroad.common.Version;

import io.quarkus.runtime.QuarkusApplication;
import jakarta.enterprise.inject.Instance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.niis.xroad.confproxy.cli.action.AbstractAction;

import java.util.Optional;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.Version.XROAD_VERSION;

/**
 * Main program for launching configuration proxy utility tools.
 */
@Slf4j
@RequiredArgsConstructor
public final class ConfProxyCLI implements QuarkusApplication {
    private static final String APP_NAME = "xroad-confproxy";
    private final Instance<AbstractAction> actions;
    private final CommandLineParser cmdLineParser = new DefaultParser();

    /**
     * Configuration proxy utility tool program entry point.
     * @param args program args
     */
    @Override
    public int run(String... args) {
        try {
            Version.outputVersionInfo(APP_NAME);
            System.out.printf("%s %s%n", APP_NAME, XROAD_VERSION);
            runUtilWithArgs(args);
            return 0;
        } catch (ExitException e) {
            System.err.println(e.getMessage());
            if (e.getCause() != null) {
                System.err.println(e.getCause());
            }
            return 1;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            log.error("Error while running confproxy util:", e);
            return 1;
        }
    }

    /**
     * Executes the utility program with the provided argument list.
     * @param args program arguments
     */
    private void runUtilWithArgs(final String[] args) throws ParseException {
        var actionName = Optional.of(args)
                .filter(params -> params.length > 0)
                .map(params -> params[0]);
        var action = actionName
                .flatMap(name -> actions.stream()
                        .filter(act -> act.getName().equals(name))
                        .findFirst());

        if (action.isPresent()) {
            CommandLine commandLine = cmdLineParser.parse(action.get().getOptions(), args);
            action.get().execute(commandLine);
        } else {
            actionName.ifPresentOrElse(
                    name -> System.err.printf("Unknown action: %s%n", name),
                    () -> System.out.println("Action name is required"));

            var availableActions = actions.stream()
                    .map(AbstractAction::getName)
                    .collect(Collectors.joining(", "));

            System.out.printf("Available actions: %s%n", availableActions);
        }
    }
}
