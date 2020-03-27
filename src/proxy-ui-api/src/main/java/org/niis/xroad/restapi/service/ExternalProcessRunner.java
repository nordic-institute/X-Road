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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ExternalProcessRunner {
    private static final long TIMEOUT = 60000;

    /**
     * Executes the given command with given arguments
     * @param command the command to execute
     * @param args arguments to be appended to the command. Make sure to pass your arguments in the correct order
     * (e.g. if your options have values enter them as separate consecutive args).
     * @return {@link ProcessResult} which contains the executed command with arguments, exit code and the output of
     * the executed process
     * @throws ProcessNotExecutableException in the case of IOException or if the process is interrupted
     * @throws ProcessFailedException if the process times out
     * @throws InterruptedException if the process running thread is interrupted. <b>The interrupted thread has already
     * been handled with so you can choose to ignore this exception if you so please.</b>
     */
    public ProcessResult execute(String command, String... args) throws ProcessNotExecutableException,
            ProcessFailedException, InterruptedException {
        if (StringUtils.isEmpty(command)) {
            throw new IllegalArgumentException("command cannot be null");
        }
        List<String> commandWithArgs = new ArrayList<>();
        commandWithArgs.add(command);
        if (args != null && args.length > 0) {
            commandWithArgs.addAll(Arrays.asList(args));
        }
        String commandWithArgsString = String.join(" ", commandWithArgs);
        log.info("Running an external command: " + commandWithArgsString);
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
            IOUtils.closeQuietly(process.getErrorStream());
            IOUtils.closeQuietly(process.getOutputStream());
            throw new ProcessNotExecutableException(e);
        }

        int exitCode;

        try {
            boolean hasExited = process.waitFor(TIMEOUT, TimeUnit.MILLISECONDS);
            // exit value cannot be asked if the process is still running after timeout - instead throw and destroy
            if (!hasExited) {
                throw new ProcessFailedException("Process timed out");
            }
            exitCode = process.exitValue();
        } catch (InterruptedException e) {
            // retain the interrupted status
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            // always destroy the process
            process.destroy();
            IOUtils.closeQuietly(process.getInputStream());
            IOUtils.closeQuietly(process.getErrorStream());
            IOUtils.closeQuietly(process.getOutputStream());
        }
        ProcessResult processResult = new ProcessResult(commandWithArgsString, exitCode, processOutput);
        return processResult;
    }

    /**
     * Executes the given command with given arguments and throws a {@link ProcessFailedException} if the process' exit
     * code is not 0 or if the process times out. Used e.g. for simple script execution when there is no need to handle
     * different exit codes
     * @param command the command to execute
     * @param args arguments to be appended to the command. Make sure to pass your arguments in the correct order
     * (e.g. if your options have values enter them as separate consecutive args).
     * @return {@link ProcessResult} which contains the executed command with arguments, exit code (always 0) and the
     * output of the executed process
     * @throws ProcessNotExecutableException in the case of IOException or if the process is interrupted
     * @throws ProcessFailedException if the process' exit code is not 0 or the process times out
     * @throws InterruptedException if the process running thread is interrupted. <b>The interrupted thread has already
     * been handled with so you can choose to ignore this exception if you so please.</b>
     */
    public ProcessResult executeAndThrowOnFailure(String command, String... args) throws ProcessNotExecutableException,
            ProcessFailedException, InterruptedException {
        ProcessResult processResult = execute(command, args);
        // if the process fails we attach the output into the exception
        if (processResult.getExitCode() != 0) {
            String lineSep = System.lineSeparator();
            String processOutputString = String.join(lineSep, processResult.processOutput);
            String errorMsg = String.format("Failed to run command '%s' with output: %n %s",
                    processResult.commandWithArgs, processOutputString);
            throw new ProcessFailedException(errorMsg);
        }
        return processResult;
    }

    @Data
    @AllArgsConstructor
    public class ProcessResult {
        private String commandWithArgs;
        private int exitCode;
        private List<String> processOutput;
    }
}
