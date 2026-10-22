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

package org.niis.xroad.e2e.parity.acl;

import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiStubsTest {
    private static final Path MAPPINGS = Path.of("src/intTest/resources/wiremock_mappings");
    private static final String PETS_PATH = "/pets/{petId}:";
    @Test
    void everySpecHasAStubServingItsYamlPath() throws IOException {
        for (var spec : OpenApiSpec.values()) {
            var name = spec.name().toLowerCase(Locale.ROOT);
            assertThat(stub(spec)).as(name).contains("\"urlPath\": \"/acl-parity/openapi/" + name + ".yaml\"");
        }
    }

    @Test
    void everySpecPointsItsServerAtTheScenarioBackendPathOfTheRequestedCode() throws IOException {
        for (var spec : OpenApiSpec.values()) {
            assertThat(stub(spec)).as(spec.name())
                    .contains("http://isrest:8080/acl-parity/{{request.query.code}}")
                    .contains("\"response-template\"");
        }
    }

    @Test
    void onlyThePetsSpecDeclaresThePetPath() throws IOException {
        for (var spec : OpenApiSpec.values()) {
            assertThat(stub(spec).contains(PETS_PATH)).as(spec.name()).isEqualTo(spec == OpenApiSpec.PETS);
        }
    }

    private static String stub(OpenApiSpec spec) throws IOException {
        return Files.readString(MAPPINGS.resolve("acl_parity_openapi_" + spec.name().toLowerCase(Locale.ROOT) + ".json"));
    }
}
