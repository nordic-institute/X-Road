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

package org.niis.xroad.e2e.parity;

import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class WsdlStubsTest {

    private static final Path MAPPINGS = Path.of("src/intTest/resources/wiremock_mappings");

    @Test
    void everyShapeHasAStubServingItsWsdlPath() throws IOException {
        for (var shape : WsdlShape.values()) {
            var name = shape.name().toLowerCase(Locale.ROOT);

            assertThat(stub(shape)).as(name).contains("\"urlPath\": \"/acl-parity/wsdl/" + name + ".wsdl\"");
        }
    }

    @Test
    void onlyTheUnversionedShapeOmitsTheOperationVersion() throws IOException {
        for (var shape : WsdlShape.values()) {
            var versioned = stub(shape).contains("<xrd:version>");

            assertThat(versioned).as(shape.name()).isEqualTo(shape != WsdlShape.SINGLE_UNVERSIONED);
        }
    }

    private static String stub(WsdlShape shape) throws IOException {
        return Files.readString(MAPPINGS.resolve("acl_parity_wsdl_" + shape.name().toLowerCase(Locale.ROOT) + ".json"));
    }
}
