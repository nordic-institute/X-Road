package org.niis.xroad.oasvalidator;

import org.junit.Test;
import org.openapi4j.core.exception.ResolutionException;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class Oas3ValidatorTest {

    @Test(expected = ResolutionException.class)
    public void validateApiSpecNotFound() throws ResolutionException {
        Oas3Validator.validateOpenApiSpec(new File("src/test/resources/not-found.yaml"));
    }

    @Test
    public void validateApiSpecSuccess() throws ResolutionException {
        int exitCode = Oas3Validator.validateOpenApiSpec(
                new File("src/test/resources/petstore-validation-success.yaml"));
        assertEquals(0, exitCode);
    }

    @Test
    public void validateApiSpecFail() throws ResolutionException {
        int exitCode = Oas3Validator.validateOpenApiSpec(
                new File("src/test/resources/petstore-validation-fail.yaml"));
        assertEquals(1, exitCode);
    }

    @Test
    public void validateApiSpecStyleFail() {
        int exitCode = Oas3Validator.validateOpenApiSpecStyle("src/test/resources/petstore-validation-style-fail.yaml");
        assertEquals(1, exitCode);
    }
}
