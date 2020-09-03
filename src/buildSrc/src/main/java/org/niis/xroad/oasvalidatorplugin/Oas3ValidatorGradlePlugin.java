/**
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
package org.niis.xroad.oasvalidatorplugin;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.openapi4j.core.exception.ResolutionException;

import java.util.ArrayList;
import java.util.List;

public class Oas3ValidatorGradlePlugin implements Plugin<Project> {
    private static final String PARAMETERS_NAME = "apiValidationParameters";
    private static final String TASK_NAME = "validateApiDefinitions";

    public Oas3ValidatorGradlePlugin() {
    }

    @Override
    public void apply(Project project) {
        Oas3ValidatorExtension extension = project.getExtensions()
                .create(PARAMETERS_NAME, Oas3ValidatorExtension.class);

        project.task(TASK_NAME)
                .doLast(task -> {
                    if (extension.getApiDefinitionPaths() == null || extension.getApiDefinitionPaths().isEmpty()) {
                        throw new GradleException("No API definition file paths provided");
                    }
                    System.out.println(System.lineSeparator() + "--- API VALIDATION START ---");
                    boolean isCompleteValidationSuccess = true;
                    List<ApiValidationResults> allValidationResults = new ArrayList<>();
                    extension.getApiDefinitionPaths().forEach(path -> {
                        try {
                            ApiValidationResults validationResults = Oas3Validator.validate(path);
                            allValidationResults.add(validationResults);
                        } catch (ResolutionException e) {
                            throw new GradleException("API definition not found", e);
                        }
                    });
                    for (ApiValidationResults apiValidationResults : allValidationResults) {
                        ApiValidationResult apiSpecValidationResult = apiValidationResults
                                .getSpecificationValidationResult();
                        ApiValidationResult styleValidationResult = apiValidationResults.getStyleValidationResult();
                        isCompleteValidationSuccess = isCompleteValidationSuccess
                                && apiSpecValidationResult.isSuccess()
                                && styleValidationResult.isSuccess();
                    }
                    System.out.println(System.lineSeparator() + "--- API VALIDATION END ---");
                    if (!isCompleteValidationSuccess) {
                        throw new GradleException("API definition validation failed");
                    }
                });
    }
}
