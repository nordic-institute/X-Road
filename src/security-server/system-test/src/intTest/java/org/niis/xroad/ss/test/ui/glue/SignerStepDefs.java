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
package org.niis.xroad.ss.test.ui.glue;

import com.nortal.test.testcontainers.TestableApplicationContainerProvider;
import io.cucumber.java.en.Step;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class SignerStepDefs extends BaseUiStepDefs {
    @Autowired
    private TestableApplicationContainerProvider containerProvider;

    @SneakyThrows
    @Step("signer service is restarted")
    public void signerServiceIsRestarted() {
        var execResult = containerProvider.getContainer()
                .execInContainer("supervisorctl", "restart", "xroad-signer");

        testReportService.attachJson("supervisorctl restart xroad-signer", execResult);
    }

    @SneakyThrows
    @Step("predefined signer softtoken is uploaded")
    public void updateSignerSoftToken() {
        execInContainer("supervisorctl", "stop", "xroad-signer");
        execInContainer("rm", "-rf", "/etc/xroad/signer/");
        execInContainer("cp", "-r", "/etc/xroad/signer-predefined/", "/etc/xroad/signer/");
        execInContainer("chown", "-R", "xroad:xroad", "/etc/xroad/signer/");
        execInContainer("supervisorctl", "start", "xroad-signer");
    }

    @SneakyThrows
    private void execInContainer(String... args) {
        var execResult = containerProvider.getContainer().execInContainer(args);
        testReportService.attachJson(StringUtils.join(args, " "), execResult);
    }
}
