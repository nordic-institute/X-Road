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
package org.niis.xroad.oasvalidatorplugin;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.openapi4j.core.exception.ResolutionException;
import org.openapi4j.core.validation.ValidationException;
import org.openapi4j.parser.OpenApi3Parser;
import org.openapitools.empoa.swagger.core.internal.SwAdapter;
import org.openapitools.openapistylevalidator.OpenApiSpecStyleValidator;
import org.openapitools.openapistylevalidator.ValidatorParameters;
import org.openapitools.openapistylevalidator.styleerror.StyleError;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public final class Oas3Validator {

    private Oas3Validator() {
    }

    public static ApiValidationResults validate(String apiSpecPath) throws ResolutionException {
        if (apiSpecPath == null) {
            throw new IllegalArgumentException("API definition document path is required");
        }
        File apiSpecFile = new File(apiSpecPath);
        String apiSpecFilePath = apiSpecFile.getAbsolutePath();
        System.out.println(System.lineSeparator() + "Validating API specification: " + apiSpecFilePath);
        ApiValidationResult specValidationResult = validateOpenApiSpec(apiSpecFile);
        ApiValidationResult styleValidationResult = validateOpenApiSpecStyle(apiSpecFilePath);
        boolean isSuccess = specValidationResult.isSuccess() && styleValidationResult.isSuccess();
        System.out.println("Validating API specification: " + apiSpecFilePath + " -> "
                + (isSuccess ? "SUCCESS" : "FAILED"));
        printValidationResults(specValidationResult);
        printValidationResults(styleValidationResult);
        ApiValidationResults apiValidationResults = new ApiValidationResults();
        apiValidationResults.setApiDefinitionPath(apiSpecPath);
        apiValidationResults.setSpecificationValidationResult(specValidationResult);
        apiValidationResults.setStyleValidationResult(styleValidationResult);
        return apiValidationResults;
    }

    private static ApiValidationResult validateOpenApiSpec(File apiSpecFile) throws ResolutionException {
        try {
            new OpenApi3Parser().parse(apiSpecFile, true);
            return ApiValidationResult.success(ApiValidationResult.ValidationType.SPECIFICATION_VALIDATION);
        } catch (ValidationException e) {
            return ApiValidationResult.fail(ApiValidationResult.ValidationType.SPECIFICATION_VALIDATION, e.results()
                    .toString());
        }
    }

    private static ApiValidationResult validateOpenApiSpecStyle(String pathToApiSpec) {
        OpenAPIParser openApiParser = new OpenAPIParser();
        SwaggerParseResult parserResult = openApiParser.readLocation(pathToApiSpec, null, null);
        // the SwaggerParseResult messages are not always very accurate
        if (parserResult.getMessages() != null && !parserResult.getMessages().isEmpty()) {
            return ApiValidationResult.fail(ApiValidationResult.ValidationType.STYLE_VALIDATION,
                    String.join(System.lineSeparator(), parserResult.getMessages()));
        }
        OpenAPI openAPI = SwAdapter.toOpenAPI(parserResult.getOpenAPI());
        OpenApiSpecStyleValidator validator = new OpenApiSpecStyleValidator(openAPI);

        // define parameters for style checking
        ValidatorParameters params = new ValidatorParameters();
        params.setParameterNamingConvention(ValidatorParameters.NamingConvention.UnderscoreCase);
        params.setPropertyNamingConvention(ValidatorParameters.NamingConvention.UnderscoreCase);
        params.setPathNamingConvention(ValidatorParameters.NamingConvention.HyphenCase);
        params.setValidateModelPropertiesExample(false);

        List<StyleError> styleErrors = validator.validate(params);
        if (styleErrors.isEmpty()) {
            return ApiValidationResult.success(ApiValidationResult.ValidationType.STYLE_VALIDATION);
        } else {
            String errorOutput = styleErrors.stream().map(StyleError::toString).collect(Collectors.joining());
            return ApiValidationResult.fail(ApiValidationResult.ValidationType.STYLE_VALIDATION,
                    errorOutput);
        }
    }

    private static void printValidationResults(ApiValidationResult validationResult) {
        boolean isValidationSuccess = validationResult.isSuccess();
        if (!isValidationSuccess) {
            String errorMessage = "--- " + validationResult.getValidationType().toString()
                    + " errors ---";
            System.err.println(errorMessage);
            System.err.println(System.lineSeparator() + validationResult.getErrorOutput());
        }
    }
}
