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

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class ExternalProcessRunner {
    /**
     * Executes the given command with given arguments.
     * @param command the command to execute
     * @param args arguments to be appended to the command. If your command has a target (e.g. a file name), make sure
     * to pass it as the last argument
     * @return the output of the executed process as a List of Strings
     * @throws ProcessNotExecutableException in the case of IOException or if the process is interrupted
     * @throws ProcessFailedException if the process' exit code is not 0
     */
    public List<String> execute(String command, String... args) throws ProcessNotExecutableException,
            ProcessFailedException {
        List<String> output = new ArrayList<>();
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
            throw new ProcessFailedException(processOutput);
        } else if (processOutput != null && processOutput.size() > 0) {
            // exitCode was 0 but there were some warnings in the output
            output.addAll(processOutput);
        }
        return output;
    }
}
