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
package org.niis.xroad.cs.test.api.globalconf;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.ConfigurationSourcesAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@SuppressWarnings("checkstyle:magicnumber")
class ConfigurationInfoApiTest extends CsApiTest {

    @Test
    void viewInternalConfiguration(CsBaselineSeeder seeder) {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new ConfigurationSourcesAdminClient(session);

        Step.when("internal configuration parts are retrieved", () ->
                client.getConfigurationParts("INTERNAL")
                        .statusCode(200)
                        .body("$", hasSize(5))
                        .body("find { it.content_identifier == 'SHARED-PARAMETERS' }.file_name", equalTo("shared-params.xml"))
                        .body("find { it.content_identifier == 'SHARED-PARAMETERS' }.version", equalTo(2))
                        .body("find { it.content_identifier == 'SHARED-PARAMETERS' }.file_updated_at", equalTo("2022-01-01T01:00:00Z"))
                        .body("find { it.content_identifier == 'SHARED-PARAMETERS' }.optional", equalTo(false))
                        .body("find { it.content_identifier == 'PRIVATE-PARAMETERS' }.file_name", equalTo("private-params.xml"))
                        .body("find { it.content_identifier == 'MONITORING' }.file_name", equalTo("test-monitoring-part.xml"))
                        .body("find { it.content_identifier == 'MONITORING' }.optional", equalTo(true))
                        .body("find { it.content_identifier == 'FETCHINTERVAL' }.file_name", equalTo("test-fetchinterval-part.xml"))
                        .body("find { it.content_identifier == 'FETCHINTERVAL' }.version", equalTo(0))
                        .body("find { it.content_identifier == 'FETCHINTERVAL' }.file_updated_at", equalTo("2022-01-01T01:00:00Z"))
                        .body("find { it.content_identifier == 'FETCHINTERVAL' }.optional", equalTo(true)));

        Step.and("internal configuration anchor info exists", () ->
                new org.niis.xroad.cs.test.api.admin.ConfigurationSourceAnchorsAdminClient(session)
                        .getAnchor("INTERNAL")
                        .statusCode(200)
                        .body("anchor.hash", notNullValue())
                        .body("anchor.created_at", notNullValue()));

        Step.and("internal configuration source download url exists", () ->
                client.getDownloadUrl("INTERNAL")
                        .statusCode(200)
                        .body("url", endsWith("/internalconf")));
    }

    @Test
    void viewExternalConfiguration(CsBaselineSeeder seeder) {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new ConfigurationSourcesAdminClient(session);

        Step.when("external configuration parts are retrieved", () ->
                client.getConfigurationParts("EXTERNAL")
                        .statusCode(200)
                        .body("$", hasSize(1))
                        .body("find { it.content_identifier == 'SHARED-PARAMETERS' }.file_name", equalTo("shared-params.xml"))
                        .body("find { it.content_identifier == 'SHARED-PARAMETERS' }.version", equalTo(2))
                        .body("find { it.content_identifier == 'SHARED-PARAMETERS' }.file_updated_at", equalTo("2022-01-01T01:00:00Z"))
                        .body("find { it.content_identifier == 'SHARED-PARAMETERS' }.optional", equalTo(false)));

        Step.and("external configuration anchor info exists", () ->
                new org.niis.xroad.cs.test.api.admin.ConfigurationSourceAnchorsAdminClient(session)
                        .getAnchor("EXTERNAL")
                        .statusCode(200)
                        .body("anchor.hash", notNullValue())
                        .body("anchor.created_at", notNullValue()));

        Step.and("external configuration source download url exists", () ->
                client.getDownloadUrl("EXTERNAL")
                        .statusCode(200)
                        .body("url", endsWith("/externalconf")));
    }

    @Test
    void downloadInternalConfigurationAnchor(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new ConfigurationSourcesAdminClient(session);

        var anchorBytes = Step.when("internal anchor file is downloaded", () ->
                client.downloadAnchor("INTERNAL")
                        .statusCode(200)
                        .header("Content-Disposition", containsString("configuration_anchor_CS_internal_UTC_"))
                        .extract().asByteArray());

        Step.then("internal anchor XML contains expected source and verification cert count", () -> {
            var doc = parseAnchorXml(anchorBytes);
            var xpath = XPathFactory.newInstance().newXPath();

            var httpCertCount = (Double) xpath
                    .compile("count(.//source[starts-with(downloadURL/text(), 'http://')]/verificationCert)")
                    .evaluate(doc, XPathConstants.NUMBER);
            assertThat(httpCertCount).as("verificationCert count for http source").isEqualTo(1.0);

            NodeList downloadUrls = (NodeList) xpath
                    .compile(".//source[starts-with(downloadURL/text(), 'http://')]/downloadURL")
                    .evaluate(doc, XPathConstants.NODESET);
            assertThat(downloadUrls.getLength()).as("http source count in internal anchor").isGreaterThanOrEqualTo(1);
            var url = downloadUrls.item(0).getTextContent();
            assertThat(url).as("internal anchor http source URL").endsWith("/internalconf");
            return null;
        });
    }

    @Test
    void downloadExternalConfigurationAnchor(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new ConfigurationSourcesAdminClient(session);

        var anchorBytes = Step.when("external anchor file is downloaded", () ->
                client.downloadAnchor("EXTERNAL")
                        .statusCode(200)
                        .header("Content-Disposition", containsString("configuration_anchor_CS_external_UTC_"))
                        .extract().asByteArray());

        Step.then("external anchor XML contains expected source and verification cert count", () -> {
            var doc = parseAnchorXml(anchorBytes);
            var xpath = XPathFactory.newInstance().newXPath();

            var httpCertCount = (Double) xpath
                    .compile("count(.//source[starts-with(downloadURL/text(), 'http://')]/verificationCert)")
                    .evaluate(doc, XPathConstants.NUMBER);
            assertThat(httpCertCount).as("verificationCert count for http source").isEqualTo(1.0);

            NodeList downloadUrls = (NodeList) xpath
                    .compile(".//source[starts-with(downloadURL/text(), 'http://')]/downloadURL")
                    .evaluate(doc, XPathConstants.NODESET);
            assertThat(downloadUrls.getLength()).as("http source count in external anchor").isGreaterThanOrEqualTo(1);
            var url = downloadUrls.item(0).getTextContent();
            assertThat(url).as("external anchor http source URL").endsWith("/externalconf");
            return null;
        });
    }

    private Document parseAnchorXml(byte[] anchorBytes) throws Exception {
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(anchorBytes));
    }

    @Test
    void downloadConfigurationPart(CsBaselineSeeder seeder) {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new ConfigurationSourcesAdminClient(session);

        Step.when("external SHARED-PARAMETERS v2 is downloaded", () ->
                client.downloadConfigurationPart("EXTERNAL", "SHARED-PARAMETERS", 2)
                        .statusCode(200)
                        .header("Content-Disposition", containsString("attachment"))
                        .header("Content-Disposition", containsString("shared-params")));

        Step.and("internal PRIVATE-PARAMETERS v2 is downloaded", () ->
                client.downloadConfigurationPart("INTERNAL", "PRIVATE-PARAMETERS", 2)
                        .statusCode(200)
                        .header("Content-Disposition", containsString("attachment"))
                        .header("Content-Disposition", containsString("private-params")));
    }

    @Test
    void uploadOptionalConfigurationPart(CsBaselineSeeder seeder) throws IOException {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new ConfigurationSourcesAdminClient(session);

        Step.given("MONITORING part has no version or fileUpdatedAt before upload", () ->
                client.getConfigurationParts("INTERNAL")
                        .statusCode(200)
                        .body("find { it.content_identifier == 'MONITORING' }.version", nullValue())
                        .body("find { it.content_identifier == 'MONITORING' }.file_updated_at", nullValue()));

        byte[] fileBytes;
        try (var stream = ClassLoader.getSystemResourceAsStream("files/monitoring-params_upload.xml")) {
            if (stream == null) {
                throw new IOException("Resource not found: files/monitoring-params_upload.xml");
            }
            fileBytes = stream.readAllBytes();
        }
        final byte[] bytes = fileBytes;

        Step.when("MONITORING file is uploaded", () ->
                client.uploadConfigurationParts("INTERNAL", "MONITORING", bytes)
                        .statusCode(204));

        Step.then("MONITORING part now has version and fileUpdatedAt", () ->
                client.getConfigurationParts("INTERNAL")
                        .statusCode(200)
                        .body("find { it.content_identifier == 'MONITORING' }.version", notNullValue())
                        .body("find { it.content_identifier == 'MONITORING' }.file_updated_at", notNullValue()));
    }

    @Test
    void uploadUnknownConfigurationPartFails(CsBaselineSeeder seeder) {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new ConfigurationSourcesAdminClient(session);
        var dummyBytes = new byte[]{0, 0, 0};

        Step.when("INTERNAL NOT-EXISTING upload fails with 500", () ->
                client.uploadConfigurationParts("INTERNAL", "NOT-EXISTING", dummyBytes)
                        .statusCode(500));

        Step.and("EXTERNAL NOT-EXISTING upload fails with 500", () ->
                client.uploadConfigurationParts("EXTERNAL", "NOT-EXISTING", dummyBytes)
                        .statusCode(500));

        Step.and("EXTERNAL PRIVATE-PARAMETERS upload fails with 500", () ->
                client.uploadConfigurationParts("EXTERNAL", "PRIVATE-PARAMETERS", dummyBytes)
                        .statusCode(500));

        Step.and("EXTERNAL FETCHINTERVAL upload fails with 500", () ->
                client.uploadConfigurationParts("EXTERNAL", "FETCHINTERVAL", dummyBytes)
                        .statusCode(500));

        Step.and("EXTERNAL MONITORING upload fails with 500", () ->
                client.uploadConfigurationParts("EXTERNAL", "MONITORING", dummyBytes)
                        .statusCode(500));
    }
}
