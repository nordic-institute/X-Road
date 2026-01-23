/*
 * The MIT License
 *
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
package org.niis.xroad.migration.pgp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgpKeyValidatorTest {

    private PgpKeyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PgpKeyValidator();
    }

    @Test
    void testValidateValidKeys() throws Exception {
        // Given: Valid test keys from scenario 1
        String secretKey = readTestResource("scenario-1-secret.asc");
        String publicKey = readTestResource("scenario-1-public.asc");

        // When: Validate keys
        PgpKeyValidator.ValidationResult result = validator.validate(secretKey, publicKey);

        // Then: Keys should be valid
        assertTrue(result.valid());
        assertNotNull(result.primaryKeyId());
        assertEquals(1, result.secretKeyCount());
        assertTrue(result.publicKeyCount() >= 1);
        assertNotNull(result.message());
    }

    @Test
    void testValidateMultipleKeys() throws Exception {
        // Given: Multiple keys from scenario 2
        String secretKey = readTestResource("scenario-2-server-secret.asc");
        String publicKeys = readTestResource("scenario-2-all-public.asc");

        // When: Validate keys
        PgpKeyValidator.ValidationResult result = validator.validate(secretKey, publicKeys);

        // Then: All keys should be valid
        assertTrue(result.valid());
        assertNotNull(result.primaryKeyId());
        assertTrue(result.publicKeyCount() >= 1);
    }

    @Test
    void testValidateInvalidSecretKey() {
        // Given: Invalid secret key
        String invalidSecret = "Invalid PGP data";
        String validPublic = "-----BEGIN PGP PUBLIC KEY BLOCK-----\n...\n-----END PGP PUBLIC KEY BLOCK-----";

        // When: Validate keys
        PgpKeyValidator.ValidationResult result = validator.validate(invalidSecret, validPublic);

        // Then: Validation should fail
        assertFalse(result.valid());
        assertNotNull(result.message());
        assertNull(result.primaryKeyId());
    }

    @Test
    void testValidateEmptyKeys() {
        // Given: Empty keys
        String emptySecret = "";
        String emptyPublic = "";

        // When: Validate keys
        PgpKeyValidator.ValidationResult result = validator.validate(emptySecret, emptyPublic);

        // Then: Validation should fail
        assertFalse(result.valid());
    }

    @Test
    void testValidationResultToString() throws Exception {
        // Given: Valid keys
        String secretKey = readTestResource("scenario-1-secret.asc");
        String publicKey = readTestResource("scenario-1-public.asc");

        // When: Validate and get string representation
        PgpKeyValidator.ValidationResult result = validator.validate(secretKey, publicKey);
        String resultString = result.toString();

        // Then: String should contain key information
        assertNotNull(resultString);
        assertTrue(resultString.contains("Valid") || resultString.contains("Key"));
    }

    @Test
    void testValidationResultFailureToString() {
        // Given: Invalid keys
        PgpKeyValidator.ValidationResult result = validator.validate("invalid", "invalid");

        // When: Get string representation
        String resultString = result.toString();

        // Then: String should indicate failure
        assertNotNull(resultString);
        assertTrue(resultString.contains("Invalid"));
    }

    private String readTestResource(String filename) throws Exception {
        Path resourcePath = Path.of("src/test/resources/archive-pgp-sample/" + filename);

        if (!Files.exists(resourcePath)) {
            resourcePath = Path.of(System.getProperty("user.dir"))
                    .getParent().getParent()
                    .resolve("src/tool/migration-cli/src/test/resources/archive-pgp-sample/" + filename);
        }

        if (!Files.exists(resourcePath)) {
            throw new RuntimeException("Test resource not found: " + filename
                    + ". Run create-test-data-simple.sh to generate test keys.");
        }

        return Files.readString(resourcePath);
    }
}


