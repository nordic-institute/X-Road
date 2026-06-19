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
package org.niis.xroad.ss.test.api.addons;

import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;
import static org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory.given;

@DisplayName("Monitoring addon scenarios")
class MonitoringAddonTest extends SsApiTest {

    private static final String PROXY = SsApiTestContainerSetup.PROXY;

    @Test
    @DisplayName("Verification configuration ZIP can be downloaded from the proxy and contains expected entries")
    void messagelogVerificationConfDownloadable(SsApiTestContainerSetup stack) {
        var verificationConfUrl = proxyBaseUrl(stack) + "/verificationconf";

        var zipBytes = when("the verification configuration is downloaded from the proxy", () ->
                given()
                        .get(verificationConfUrl)
                        .then()
                        .statusCode(200)
                        .extract()
                        .asByteArray());

        then("the ZIP contains the expected shared-params and instance-identifier entries", () -> {
            var entries = zipEntryNames(zipBytes);
            assertThat(entries).contains(
                    "verificationconf/DEV/shared-params.xml",
                    "verificationconf/DEV/shared-params.xml.metadata",
                    "verificationconf/instance-identifier");
        });
    }

    private String proxyBaseUrl(SsApiTestContainerSetup stack) {
        var mapping = stack.getContainerMapping(PROXY, Port.PROXY_HTTP);
        return "http://%s:%d".formatted(mapping.host(), mapping.port());
    }

    @SneakyThrows
    private static List<String> zipEntryNames(byte[] zipBytes) {
        var names = new ArrayList<String>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                zip.closeEntry();
            }
        }
        return names;
    }
}
