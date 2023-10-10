/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.test.glue;

import com.nortal.test.asserts.Assertion;
import feign.FeignException;
import io.cucumber.java.en.Step;
import org.apache.commons.io.IOUtils;
import org.niis.xroad.cs.openapi.model.ConfigurationAnchorContainerDto;
import org.niis.xroad.cs.openapi.model.ConfigurationPartDto;
import org.niis.xroad.cs.openapi.model.ConfigurationTypeDto;
import org.niis.xroad.cs.openapi.model.GlobalConfDownloadUrlDto;
import org.niis.xroad.cs.test.api.FeignConfigurationPartsApi;
import org.niis.xroad.cs.test.api.FeignConfigurationSourceAnchorApi;
import org.niis.xroad.cs.test.api.FeignConfigurationSourcesApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static com.nortal.test.asserts.Assertions.notNullAssertion;
import static java.lang.ClassLoader.getSystemResource;
import static java.nio.file.Files.readAllBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.niis.xroad.cs.openapi.model.ConfigurationTypeDto.EXTERNAL;
import static org.niis.xroad.cs.openapi.model.ConfigurationTypeDto.INTERNAL;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_XML;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ConfigurationInfoApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignConfigurationSourceAnchorApi configurationSourceAnchorApi;
    @Autowired
    private FeignConfigurationSourcesApi configurationSourcesApi;
    @Autowired
    private FeignConfigurationPartsApi configurationPartsApi;

    private static final long EXPECTED_INTERNAL_CONFIGURATION_ANCHOR_CONTENT_LENGTH = 1348L;
    private static final long EXPECTED_EXTERNAL_CONFIGURATION_ANCHOR_CONTENT_LENGTH = 1331L;

    private ResponseEntity<Resource> downloadedAnchor;

    @Step("EXTERNAL configuration parts exists")
    public void viewExternalConfParts() {
        final ResponseEntity<List<ConfigurationPartDto>> response = configurationPartsApi
                .getConfigurationParts(EXTERNAL);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1, "body.size()", "Response contains 1 item"))
                .assertion(equalsAssertion("SHARED-PARAMETERS", "body[0].contentIdentifier",
                        "Response contains content identifier"))
                .assertion(equalsAssertion("shared-params.xml", "body[0].fileName",
                        "Response contains file name "))
                .assertion(equalsAssertion(OffsetDateTime.parse("2022-01-01T01:00Z"), "body[0].fileUpdatedAt",
                        "Response contains date at which file was updated"))
                .assertion(equalsAssertion(2, "body[0].version",
                        "Response contains version "))
                .assertion(equalsAssertion(false, "body[0].optional",
                        "Configuration part is mandatory"))
                .execute();
    }

    @Step("INTERNAL configuration parts exists")
    @SuppressWarnings("checkstyle:MagicNumber")
    public void viewInternalConfParts() {
        final ResponseEntity<List<ConfigurationPartDto>> response = configurationPartsApi
                .getConfigurationParts(INTERNAL);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(4, "body.size()", "Response contains 2 items"))

                .assertion(equalsAssertion("shared-params.xml",
                        "body.^[contentIdentifier=='SHARED-PARAMETERS'].fileName", "File name matches"))
                .assertion(equalsAssertion(2,
                        "body.^[contentIdentifier=='SHARED-PARAMETERS'].version", "Version matches"))
                .assertion(equalsAssertion(OffsetDateTime.parse("2022-01-01T01:00Z"),
                        "body.^[contentIdentifier=='SHARED-PARAMETERS'].fileUpdatedAt", "UpdatedAt matches"))
                .assertion(equalsAssertion(false,
                        "body.^[contentIdentifier=='SHARED-PARAMETERS'].optional", "Part is mandatory"))

                .assertion(equalsAssertion("private-params.xml",
                        "body.^[contentIdentifier=='PRIVATE-PARAMETERS'].fileName", "File name matches"))
                .assertion(equalsAssertion(2,
                        "body.^[contentIdentifier=='SHARED-PARAMETERS'].version", "Version matches"))
                .assertion(equalsAssertion(OffsetDateTime.parse("2022-01-01T01:00Z"),
                        "body.^[contentIdentifier=='SHARED-PARAMETERS'].fileUpdatedAt", "UpdatedAt matches"))
                .assertion(equalsAssertion(false,
                        "body.^[contentIdentifier=='SHARED-PARAMETERS'].optional", "Part is mandatory"))

                .assertion(equalsAssertion("test-monitoring-part.xml",
                        "body.^[contentIdentifier=='MONITORING'].fileName", "File name matches"))
                .assertion(isNull("body.^[contentIdentifier=='MONITORING'].version"))
                .assertion(isNull("body.^[contentIdentifier=='MONITORING'].fileUpdatedAt"))
                .assertion(equalsAssertion(true,
                        "body.^[contentIdentifier=='MONITORING'].optional", "Part is optional"))

                .assertion(equalsAssertion("test-fetchinterval-part.xml",
                        "body.^[contentIdentifier=='FETCHINTERVAL'].fileName", "File name matches"))
                .assertion(equalsAssertion(0, "body.^[contentIdentifier=='FETCHINTERVAL'].version", "Version matches"))
                .assertion(equalsAssertion(OffsetDateTime.parse("2022-01-01T01:00Z"),
                        "body.^[contentIdentifier=='FETCHINTERVAL'].fileUpdatedAt", "UpdatedAt matches"))
                .assertion(equalsAssertion(true,
                        "body.^[contentIdentifier=='FETCHINTERVAL'].optional", "Part is optional"))

                .execute();
    }

    @Step("{} configuration source anchor info exists")
    public void viewSourceAnchor(String configurationType) {
        final ResponseEntity<ConfigurationAnchorContainerDto> response = configurationSourceAnchorApi
                .getAnchor(ConfigurationTypeDto.fromValue(configurationType));

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion("4D:72:A8:60:90:88:A2:5B:9C:6B:91:86:3C:D7:44:CE:9E:E1:1C:27:8E:33:F4:E5:31:68:F2:EC",
                        "body.anchor.hash",
                        "Response contains file hash"))
                .assertion(equalsAssertion(OffsetDateTime.parse("2022-01-01T01:00Z"), "body.anchor.createdAt",
                        "Response contains created at date"))
                .execute();
    }

    @Step("{} configuration source global download url exists")
    public void viewSourceDownloadUrl(String configurationType) {
        final ResponseEntity<GlobalConfDownloadUrlDto> response = configurationSourcesApi
                .getDownloadUrl(ConfigurationTypeDto.fromValue(configurationType));

        String expectedDownloadUrl = "https://cs/" + configurationType.toLowerCase() + "conf";
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(expectedDownloadUrl, "body.url", "Response contains global download url"))
                .execute();
    }

    @Step("user downloads {} configuration source anchor")
    public void downloadConfigurationSource(String configurationType) {
        downloadedAnchor = configurationSourceAnchorApi
                .downloadAnchor(ConfigurationTypeDto.fromValue(configurationType));
    }

    @Step("it should return internal configuration source anchor file with filename {string}")
    public void validateInternalDownloadedAnchor(String filename) throws IOException {
        String expectedAnchorContent = IOUtils.resourceToString("/test-data/internal-configuration-anchor.xml",
                StandardCharsets.UTF_8);
        validate(downloadedAnchor)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(EXPECTED_INTERNAL_CONFIGURATION_ANCHOR_CONTENT_LENGTH, "body.contentLength",
                        "Response contains configuration anchor has correct content length"))
                .assertion(equalsAssertion(filename, "body.filename",
                        "Configuration anchor file has correct name"))
                .assertion(equalsAssertion(expectedAnchorContent,
                        "T(org.apache.commons.io.IOUtils).toString(body.inputStream, 'UTF-8')",
                        "Configuration anchor file content"))
                .execute();
    }

    @Step("it should return external configuration source anchor file with filename {string}")
    public void validateExternalDownloadedAnchor(String filename) throws IOException {
        String expectedAnchorContent = IOUtils.resourceToString("/test-data/external-configuration-anchor.xml",
                StandardCharsets.UTF_8);
        validate(downloadedAnchor)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(EXPECTED_EXTERNAL_CONFIGURATION_ANCHOR_CONTENT_LENGTH, "body.contentLength",
                        "Response contains configuration anchor has correct content length"))
                .assertion(equalsAssertion(filename, "body.filename",
                        "Configuration anchor file has correct name"))
                .assertion(equalsAssertion(expectedAnchorContent,
                        "T(org.apache.commons.io.IOUtils).toString(body.inputStream, 'UTF-8')",
                        "Configuration anchor file content"))
                .execute();
    }

    @Step("User can download {} configuration part {} version {}")
    public void downloadConfigurationPart(String configurationType, String contentIdentifier, int version) {
        final ResponseEntity<Resource> response = configurationPartsApi.downloadConfigurationParts(
                ConfigurationTypeDto.valueOf(configurationType), contentIdentifier, version);

        final HttpHeaders headers = response.getHeaders();

        assertEquals(OK, response.getStatusCode());
        assertEquals(resolveFileNamePart(contentIdentifier) + "_2022-01-01_01 00 00.xml", headers.getContentDisposition().getFilename());
        assertEquals("attachment", headers.getContentDisposition().getType());
        assertEquals(APPLICATION_XML, headers.getContentType());
    }

    private String resolveFileNamePart(String contentIdentifier) {
        switch (contentIdentifier) {
            case "SHARED-PARAMETERS":
                return "shared-params";
            case "PRIVATE-PARAMETERS":
                return "private-params";
            default:
                throw new RuntimeException();
        }
    }

    @Step("{} configuration part {} was not uploaded")
    public void configurationPartWasNotUploaded(String configurationType, String contentIdentifier) {
        final ResponseEntity<List<ConfigurationPartDto>> response = configurationPartsApi
                .getConfigurationParts(ConfigurationTypeDto.fromValue(configurationType));

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1,
                        "body.?[contentIdentifier=='" + contentIdentifier + "'].size()", "Response contains part: " + contentIdentifier))
                .assertion(isNull("body.^[contentIdentifier=='" + contentIdentifier + "'].version"))
                .assertion(isNull("body.^[contentIdentifier=='" + contentIdentifier + "'].fileUpdatedAt"))
                .execute();
    }

    @Step("user uploads {} configuration {} file {}")
    public void userUploadsConfigurationPart(String configurationType, String contentIdentifier, String filename) throws Exception {
        MultipartFile file = new MockMultipartFile("file", "file.xml", null,
                readAllBytes(Paths.get(getSystemResource("files/" + filename).toURI())));

        final ResponseEntity<Void> response = configurationPartsApi
                .uploadConfigurationParts(ConfigurationTypeDto.fromValue(configurationType), contentIdentifier, file);

        validate(response)
                .assertion(equalsStatusCodeAssertion(NO_CONTENT))
                .execute();
    }

    @Step("{} configuration part {} is updated")
    public void configurationPartIsUpdated(String configurationType, String contentIdentifier) {
        final ResponseEntity<List<ConfigurationPartDto>> response = configurationPartsApi
                .getConfigurationParts(ConfigurationTypeDto.fromValue(configurationType));

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1,
                        "body.?[contentIdentifier=='" + contentIdentifier + "'].size()",
                        "Response contains part: " + contentIdentifier))
                .assertion(notNullAssertion("body.^[contentIdentifier=='" + contentIdentifier + "'].version"))
                .assertion(notNullAssertion("body.^[contentIdentifier=='" + contentIdentifier + "'].fileUpdatedAt"))
                .execute();
    }

    @Step("{} configuration part {} file upload fails")
    public void uploadingConfigurationPartFails(String configurationType, String contentIdentifier) {
        MultipartFile file = new MockMultipartFile("file", "file.xml", null, new byte[]{0, 0, 0});
        try {
            configurationPartsApi
                    .uploadConfigurationParts(ConfigurationTypeDto.fromValue(configurationType), contentIdentifier, file);
            fail("Should throw exception");
        } catch (FeignException feignException) {
            validate(feignException.status())
                    .assertion(new Assertion.Builder()
                            .message("Verify status code")
                            .expression("=")
                            .actualValue(feignException.status())
                            .expectedValue(INTERNAL_SERVER_ERROR.value())
                            .build())
                    .execute();
        }
    }
}
