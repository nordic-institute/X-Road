package org.niis.xroad.restapi.service;

import org.niis.xroad.restapi.exceptions.ErrorDeviation;
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
     * @throws ProcessFailedException if the process' return code is not 0
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
            throw new ProcessNotExecutableException(e, commandWithArgs);
        }

        // gather output into a list of string for returning
        List<String> processOutput = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            br.lines().forEach(processOutput::add);
        } catch (IOException e) {
            process.destroy();
            throw new ProcessNotExecutableException(e, commandWithArgs);
        }

        int exitCode;

        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            // we don't want to throw the InterruptedException from here but we want to retain the interrupted status
            Thread.currentThread().interrupt();
            throw new ProcessNotExecutableException(e, commandWithArgs);
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

    public static class ProcessNotExecutableException extends ServiceException {
        public static final String PROCESS_NOT_EXECUTABLE = "process_not_executable";

        public ProcessNotExecutableException(Throwable t, List<String> s) {
            super(t, new ErrorDeviation(PROCESS_NOT_EXECUTABLE, s));
        }
    }

    public static class ProcessFailedException extends ServiceException {
        public static final String PROCESS_FAILED = "process_failed";

        public ProcessFailedException(List<String> s) {
            super(new ErrorDeviation(PROCESS_FAILED, s));
        }
    }
}
