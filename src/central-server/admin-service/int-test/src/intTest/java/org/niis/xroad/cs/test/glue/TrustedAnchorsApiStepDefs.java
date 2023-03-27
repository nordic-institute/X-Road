/*
 * The MIT License
 * <p>
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


import feign.FeignException;
import io.cucumber.java.en.Step;
import org.apache.commons.io.IOUtils;
import org.niis.xroad.cs.openapi.model.TrustedAnchorDto;
import org.niis.xroad.cs.test.api.FeignTrustedAnchorsApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static com.nortal.test.asserts.Assertions.notNullAssertion;
import static java.lang.ClassLoader.getSystemResource;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class TrustedAnchorsApiStepDefs extends BaseStepDefs {
    @Autowired
    private FeignTrustedAnchorsApi trustedAnchorsApi;

    @Step("user uploads trusted anchor {string} for preview")
    public void userUploadsTrustedAnchorFileForPreview(String filename) {
        try {
            var result = trustedAnchorsApi.previewTrustedAnchor(getFileAsResource(filename));
            putStepData(StepDataKey.RESPONSE_STATUS, result.getStatusCodeValue());
            putStepData(StepDataKey.RESPONSE_BODY, result.getBody());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    private Resource getFileAsResource(String filename) {
        return new FileUrlResource(getSystemResource("files/trusted-anchor/" + filename));
    }

    @Step("trusted anchor file {string} is uploaded")
    public void userUploadsTrustedAnchorFile(String filename) {
        try {
            var result = trustedAnchorsApi.uploadTrustedAnchor(getFileAsResource(filename));
            putStepData(StepDataKey.RESPONSE_STATUS, result.getStatusCodeValue());
            putStepData(StepDataKey.RESPONSE_BODY, result.getBody());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("trusted anchor with hash: {string} is downloaded")
    public void trustedAnchorIsDownloaded(String hash) {
        try {
            var result = trustedAnchorsApi.downloadTrustedAnchor(hash);
            putStepData(StepDataKey.RESPONSE_STATUS, result.getStatusCodeValue());
            putStepData(StepDataKey.RESPONSE, result);
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("uploaded trusted anchor is downloaded")
    public void trustedAnchorIsDownloaded() {
        final TrustedAnchorDto uploadedAnchor = getRequiredStepData(StepDataKey.RESPONSE_BODY);
        trustedAnchorIsDownloaded(uploadedAnchor.getHash());
    }

    @Step("download anchor matches trusted anchor file {string}")
    public void matchesTrustedAnchor(String filename) throws IOException {
        ResponseEntity<Resource> downloadedAnchor = getRequiredStepData(StepDataKey.RESPONSE);

        String expectedAnchorContent = IOUtils.resourceToString("/files/trusted-anchor/" + filename,
                StandardCharsets.UTF_8);
        validate(downloadedAnchor)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(notNullAssertion("body.filename"))
                .assertion(equalsAssertion(expectedAnchorContent,
                        "T(org.apache.commons.io.IOUtils).toString(body.inputStream, 'UTF-8')",
                        "Trusted anchor file content"))
                .execute();
    }

    @Step("trusted anchor response contains instance {string} and hash {string}")
    public void validateTrustedAnchorResponse(String instanceid, String hash) {
        final TrustedAnchorDto response = getRequiredStepData(StepDataKey.RESPONSE_BODY);

        validate(response)
                .assertion(equalsAssertion(hash, "hash"))
                .assertion(equalsAssertion(instanceid, "instanceIdentifier"))
                .assertion(notNullAssertion("generatedAt"))
                .execute();
    }

    @Step("trusted anchors list contains hash {string}")
    public void trustedAnchorsListContainsHash(String hash) {
        final List<TrustedAnchorDto> response = getRequiredStepData(StepDataKey.RESPONSE_BODY);

        validate(response)
                .assertion(equalsAssertion(1, "#this.?[hash == '" + hash + "'].size()"))
                .execute();
    }

    @Step("trusted anchors list contains {int} items")
    public void trustedAnchorsListContainsItems(int size) {
        final List<TrustedAnchorDto> response = getRequiredStepData(StepDataKey.RESPONSE_BODY);
        validate(response)
                .assertion(equalsAssertion(size, "#this.size()"))
                .execute();
    }

    @Step("trusted anchors list is retrieved")
    public void trustedAnchorsListIsRetrieved() {
        try {
            var result = trustedAnchorsApi.getTrustedAnchors();
            putStepData(StepDataKey.RESPONSE_STATUS, result.getStatusCodeValue());
            putStepData(StepDataKey.RESPONSE_BODY, result.getBody());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("trusted anchor is deleted by hash {string}")
    public void trustedAnchorIsDeletedByHash(String hash) {
        try {
            var result = trustedAnchorsApi.deleteTrustedAnchor(hash);
            putStepData(StepDataKey.RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }
}
