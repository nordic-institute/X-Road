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
package org.niis.xroad.e2e.glue;

import ee.ria.xroad.common.asic.AsicContainerVerifier;

import io.cucumber.docstring.DocString;
import io.cucumber.java.en.Step;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponseOptions;
import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.niis.xroad.e2e.EnvSetup;
import org.niis.xroad.e2e.database.TestDatabaseService;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.niis.xroad.test.globalconf.TestGlobalConfFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.restassured.RestAssured.given;
import static io.restassured.config.XmlConfig.xmlConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

@SuppressWarnings(value = {"SpringJavaInjectionPointsAutowiringInspection"})
public class ProxyStepDefs extends BaseE2EStepDefs {
    private static final String HEADER_CLIENT_ID = "x-road-client";

    @Autowired
    private EnvSetup envSetup;
    @Autowired
    private TestDatabaseService testDatabaseService;
    @Autowired
    private TestFrameworkCoreProperties coreProperties;

    private ValidatableResponseOptions<?, ?> response;

    @Step("SOAP request is sent to {string} {string}")
    public void requestSoapIsSentToProxy(String env, String service, DocString docString) {
        var mapping = envSetup.getContainerMapping(env, service, EnvSetup.Port.PROXY);

        response = given()
                .config(RestAssured.config()
                        .xmlConfig(xmlConfig()
                                .namespaceAware(true)
                                .declareNamespace("soapenv", "http://schemas.xmlsoap.org/soap/envelope/")))
                .body(docString.getContent())
                .header(HttpHeaders.CONTENT_TYPE, "text/xml")
                .post("http://%s:%s".formatted(mapping.host(), mapping.port()))
                .then();
    }

    @Step("response is received with http status code {int} and body path {string} is equal to {string}")
    public void responseValidated(int httpStatus, String path, String value) {
        response.assertThat()
                .statusCode(httpStatus)
                .body(path, equalTo(value));
    }

    @Step("response is received with http status code {int} and body path {string} is not empty")
    public void responseValidated(int httpStatus, String path) {
        response.assertThat()
                .statusCode(httpStatus)
                .body(path, notNullValue());
    }

    @Step("REST request is sent to {string} {string}")
    public void requestRestIsSentToProxy(String env, String service, DocString docString) {
        var mapping = envSetup.getContainerMapping(env, service, EnvSetup.Port.PROXY);

        response = given()
                .body(docString.getContent())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HEADER_CLIENT_ID, "DEV/COM/4321/TestClient")
                .post("http://%s:%s/r1/DEV/COM/1234/TestService/mock1".formatted(mapping.host(), mapping.port()))
                .then();
    }

    @Step("REST request targeted at {string} API endpoint is sent to {string} {string}")
    public void requestOpenapiRestIsSentToProxy(String apiEndpoint, String env, String service) {
        var mapping = envSetup.getContainerMapping(env, service, EnvSetup.Port.PROXY);

        response = given()
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HEADER_CLIENT_ID, "DEV/COM/4321/TestClient")
                .get("http://%s:%s/r1/DEV/COM/1234/TestService/restapi/%s"
                        .formatted(mapping.host(), mapping.port(), apiEndpoint.replaceFirst("^/", "")))
                .then();
    }

    @Step("Waiting for {int} seconds to ensure that all messagelogs are archived and removed from database")
    public void waitForMessagelogsToBeArchivedAndRemovedFromDatabase(int seconds) throws InterruptedException {
        TimeUnit.SECONDS.sleep(seconds);
    }

    @Step("Global configuration is fetched from {string}'s {string} for messagelog verification")
    public void globalConfIsFetchedForMessagelogValidation(String env, String service) throws IOException {
        var mapping = envSetup.getContainerMapping(env, service, EnvSetup.Port.PROXY);

        try (var zis = new ZipInputStream(given()
                .get("http://%s:%s/verificationconf".formatted(mapping.host(), mapping.port()))
                .asInputStream())) {


            for (var entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                var path = Path.of(coreProperties.resourceDir()).resolve(entry.getName());
                if (!entry.isDirectory()) {
                    Files.createDirectories(path.getParent());
                    Files.copy(zis, path, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    @Step("{string}'s {string} service has {int} messagelogs present in the archives and all are cryptographically valid")
    public void serviceHasMessagelogArchivePresent(String env, String service, int expectedMessagelogCount)
            throws IOException, InterruptedException {
        var localCompressedArchivesPath = coreProperties.resourceDir() + "messagelog-archives.tar.gz";
        var container = envSetup.getContainerByServiceName(env, service).orElseThrow();
        container.execInContainer("tar", "czf", "/tmp/messagelog-archives.tar.gz", "-C", "/var/lib/xroad", ".");
        container.copyFileFromContainer("/tmp/messagelog-archives.tar.gz", localCompressedArchivesPath);
        container.execInContainer("rm", "/tmp/messagelog-archives.tar.gz");

        try (var tis = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(localCompressedArchivesPath)))) {
            var messagelogCount = 0;
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                if (entry.getName().equals("./")) {
                    continue;
                }
                assertThat(entry.getName()).matches("\\./mlog-.*\\.zip");
                try (ByteArrayInputStream bais = new ByteArrayInputStream(tis.readAllBytes());
                     ZipInputStream zis = new ZipInputStream(bais)) {
                    ZipEntry archiveEntry;
                    while ((archiveEntry = zis.getNextEntry()) != null) {
                        if (archiveEntry.getName().equals("linkinginfo")) {
                            continue;
                        }
                        assertThat(archiveEntry.getName()).endsWith(".asice");
                        var tmpAsiceContainer = Files.write(Path.of(coreProperties.resourceDir(),
                                archiveEntry.getName()), zis.readAllBytes());
                        verifyMessagelog(tmpAsiceContainer);
                        Files.delete(tmpAsiceContainer);
                        messagelogCount++;
                    }
                }
            }
            assertThat(messagelogCount).isEqualTo(expectedMessagelogCount);
        }
    }

    @SneakyThrows
    private void verifyMessagelog(Path asiceContainer) {
        new AsicContainerVerifier(
                TestGlobalConfFactory.create(coreProperties.resourceDir() + "verificationconf"),
                new OcspVerifierFactory(),
                asiceContainer.toString()
        ).verify();
    }

    @Step("{string} contains {int} messagelog entries")
    public void messageLogContainsNEntries(String env, int expectedCount) {
        String sql = "SELECT COUNT(id) FROM logrecord";
        final Integer recordsCount = testDatabaseService.getMessagelogTemplate(env)
                .queryForObject(sql, Map.of(), Integer.class);
        assertThat(recordsCount).isEqualTo(expectedCount);
    }

}
