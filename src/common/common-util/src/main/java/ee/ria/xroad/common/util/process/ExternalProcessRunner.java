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
package ee.ria.xroad.common.util.process;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ExternalProcessRunner {
    private static final long TIMEOUT = 60000;

    /**
     * Executes the given command with given arguments.
     * <b>Notice that arguments should be provided as varargs or as an array without any whitespace</b>
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
        if (StringUtils.isBlank(command)) {
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
            log.error("Error starting external process: " + command, e);
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
            log.error("External command not executable: " + commandWithArgsString, e);
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
            log.info("External command finished with exit status {}", exitCode);
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
     * different exit codes. <b>Notice that arguments should be provided as varargs or as an array without any
     * whitespace</b>
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
            String processOutputString = processOutputToString(processResult.processOutput);
            String errorMsg = String.format("Failed to run command '%s' with output: %n %s",
                    processResult.commandWithArgs, processOutputString);
            log.error(errorMsg);
            throw new ProcessFailedException(errorMsg, processResult.processOutput);
        }
        return processResult;
    }

    /**
     * Format the process output string list to one string
     * @param processOutput
     * @return
     */
    public static String processOutputToString(List<String> processOutput) {
        String lineSep = System.lineSeparator();
        return String.join(lineSep, processOutput);
    }

    @Data
    @AllArgsConstructor
    public static class ProcessResult {
        private String commandWithArgs;
        private int exitCode;
        private List<String> processOutput;
    }
}
