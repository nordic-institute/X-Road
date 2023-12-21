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
package org.niis.xroad.common.test.glue;

import io.cucumber.java.en.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.niis.xroad.common.test.api.TestCaFeignApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

@Slf4j
public class TestCaStepDefs extends BaseStepDefs {
    @Autowired
    private TestCaFeignApi testCaFeignApi;

    @Step("AUTH CSR is processed by test CA")
    public void authCsrIsBeingProcessed() {
        csrIsBeingProcessed(TestCaFeignApi.CsrType.AUTH);
    }

    @Step("SIGN CSR is processed by test CA")
    public void signCsrIsBeingProcessed() {
        csrIsBeingProcessed(TestCaFeignApi.CsrType.SIGN);
    }

    @Step("CSR is processed by test CA")
    public void csrIsBeingProcessed() {
        csrIsBeingProcessed(TestCaFeignApi.CsrType.AUTO);
    }

    @SneakyThrows
    @SuppressWarnings("squid:S5443")
    private void csrIsBeingProcessed(TestCaFeignApi.CsrType csrType) {
        Optional<File> csrFileOpt = getStepData(StepDataKey.DOWNLOADED_FILE);
        File csrFile = csrFileOpt.orElseThrow();
        log.info("Processing downloaded file {}", csrFile);
        ResponseEntity<byte[]> certResponse = testCaFeignApi.signCert(convert(csrFile), csrType);

        File cert = File.createTempFile("tmp", "cert" + System.currentTimeMillis());
        FileUtils.writeByteArrayToFile(cert, certResponse.getBody());
        putStepData(StepDataKey.CERT_FILE, cert);
    }

    @SneakyThrows
    public static MultipartFile convert(File file) {
        return new MockMultipartFile("file", file.getName(), Files.probeContentType(file.toPath()), Files.newInputStream(file.toPath()));
    }
}
