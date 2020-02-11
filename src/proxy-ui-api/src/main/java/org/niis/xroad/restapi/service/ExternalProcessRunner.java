/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class ExternalProcessRunner {
    /**
     * Executes the given command with given arguments.
     * @param command the command to execute
     * @param args arguments to be appended to the command. Make sure to pass your arguments in the correct order
     * (e.g. if your options have values enter them as separate consecutive args).
     * @return the output of the executed process as a List of Strings
     * @throws ProcessNotExecutableException in the case of IOException or if the process is interrupted
     * @throws ProcessFailedException if the process' exit code is not 0
     */
    public List<String> execute(String command, String... args) throws ProcessNotExecutableException,
            ProcessFailedException {
        if (StringUtils.isEmpty(command)) {
            throw new IllegalArgumentException("command cannot be null");
        }

        List<String> commandWithArgs = new ArrayList<>();
        commandWithArgs.add(command);
        if (args != null && args.length > 0) {
            commandWithArgs.addAll(Arrays.asList(args));
        }
        Process process;
        ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
        // redirect process errors into process's input stream
        pb.redirectErrorStream(true);
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new ProcessNotExecutableException(e);
        }

        // gather output into a list of string for returning
        List<String> processOutput = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            br.lines().forEach(processOutput::add);
        } catch (IOException e) {
            process.destroy();
            throw new ProcessNotExecutableException(e);
        }

        int exitCode;

        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            // we don't want to throw the InterruptedException from here but we want to retain the interrupted status
            Thread.currentThread().interrupt();
            throw new ProcessNotExecutableException(e);
        } finally {
            // always destroy the process
            process.destroy();
        }

        // if the process fails we attach the output into the exception
        if (exitCode != 0) {
            String fullCommandString = String.join(" ", commandWithArgs);
            String processOutputString = String.join("\n", processOutput);
            String errorMsg = String.format("Failed to run command '%s' with output: \n %s", fullCommandString,
                    processOutputString);
            throw new ProcessFailedException(errorMsg, processOutput);
        }
        return processOutput;
    }
}
