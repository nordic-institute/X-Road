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
package org.niis.xroad.restapi.wsdl;

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ErrorCode;
import org.niis.xroad.restapi.exceptions.WsdlValidationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WsdlValidator as done in X-Road addons: wsdlvalidator
 */
@Slf4j
@Component
public final class WsdlValidator {
    public static final String WSDL_VALIDATOR_NOT_EXECUTABLE = "clients.wsdl_validator_not_executable";
    public static final String WSDL_VALIDATION_FAILED = "clients.wsdl_validation_failed";
    public static final String WSDL_URL_MISSING = "clients.wsdl_url_missing";
    public static final String WSDL_VALIDATION_WARNINGS = "clients.wsdl_validation_warnings";

    private final String wsdlUrl;

    private String wsdlValidatorCommand;
    private List<String> args;

    public WsdlValidator(String wsdlUrl) {
        this.wsdlUrl = wsdlUrl;
        wsdlValidatorCommand = SystemProperties.getWsdlValidatorCommand();
    }

    /**
     * validate WSDL with user selected validator
     * @throws WsdlValidationException when validator is not found,
     *                                 wsdl url is missing, there are errors when trying
     *                                 to execute the validator or if the validation itself fails.
     *                                 ErrorCodes are attached to the exception
     */
    public void executeValidator() throws WsdlValidationException {
        // validator not set - this is ok since validator is optional
        if (StringUtils.isEmpty(wsdlValidatorCommand)) {
            return;
        }
        if (StringUtils.isEmpty(wsdlUrl)) {
            throw new WsdlValidationException(ErrorCode.of(WSDL_URL_MISSING));
        }

        List<String> command = new ArrayList<>();
        command.add(wsdlValidatorCommand);
        if (args != null && args.size() > 0) {
            command.addAll(args);
        }
        command.add(wsdlUrl);
        Process process;
        ProcessBuilder pb = new ProcessBuilder(command);
        // redirect process errors into process's input stream
        pb.redirectErrorStream(true);
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new WsdlValidationException(e,
                    createValidationWarningMap(WSDL_VALIDATOR_NOT_EXECUTABLE, e.getCause().getMessage()));
        }

        // gather output into a list of string - needed when returning warnings to the end user
        List<String> processOutput = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            br.lines().forEach(processOutput::add);
        } catch (IOException e) {
            throw new WsdlValidationException(e,
                    createValidationWarningMap(WSDL_VALIDATOR_NOT_EXECUTABLE, e.getCause().getMessage()));
        }

        int exitCode;

        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            throw new WsdlValidationException(e,
                    createValidationWarningMap(WSDL_VALIDATOR_NOT_EXECUTABLE, e.getCause().getMessage()));
        }

        // if the validator program fails we attach the validator's output into the exception
        if (exitCode != 0) {
            throw new WsdlValidationException(createValidationWarningMap(WSDL_VALIDATION_FAILED, processOutput));
        }
    }

    public String getWsdlValidatorCommand() {
        return wsdlValidatorCommand;
    }

    public void setWsdlValidatorCommand(String wsdlValidatorCommand) {
        this.wsdlValidatorCommand = wsdlValidatorCommand;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    private Map<String, List<String>> createValidationWarningMap(String error, List<String> warningList) {
        Map<String, List<String>> warningMap = new HashMap<>();
        warningMap.put(error, warningList);
        return warningMap;
    }

    private Map<String, List<String>> createValidationWarningMap(String error, String warning) {
        return createValidationWarningMap(error, Collections.singletonList(warning));
    }
}
