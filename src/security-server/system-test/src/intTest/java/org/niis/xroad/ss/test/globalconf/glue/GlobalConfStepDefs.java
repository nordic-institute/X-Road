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
package org.niis.xroad.ss.test.globalconf.glue;

import ee.ria.xroad.common.conf.globalconf.ConfigurationPartMetadata;

import com.codeborne.selenide.Selenide;
import com.nortal.test.testcontainers.TestContainerService;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.container.ReverseProxyAuxiliaryContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.utility.MountableFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalConfStepDefs {

    @Autowired
    ReverseProxyAuxiliaryContainer reverseProxyAuxiliaryContainer;

    @Autowired
    TestContainerService testContainerService;

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("Central Server's global conf is updated by a new active signing key")
    public void centralServersGlobalConfSignKeysAreRotated() {
        var newGlobalConfFiles = MountableFile.forClasspathResource("files/global_conf_signed_with_rotated_keys");
        reverseProxyAuxiliaryContainer.getTestContainer().copyFileToContainer(newGlobalConfFiles, "/var/lib/xroad/public");

        // As Security server polls for global conf every 3 secs, ensure that SS has the new global conf loaded
        Selenide.sleep(5000);
    }

    @Step("Security Server's global conf expiration date is equal to {}")
    public void securityServersGlobalConfExpirationDateIsEqualTo(String expectedExpirationDateTime)
            throws IOException, InterruptedException {
        String metadataJson = testContainerService.getContainer()
                .execInContainer("cat", "/etc/xroad/globalconf/CS/shared-params.xml.metadata").getStdout();
        var metadata = ConfigurationPartMetadata.read(new ByteArrayInputStream(metadataJson.getBytes()));
        assertThat(metadata.getExpirationDate()).isEqualTo(expectedExpirationDateTime);
    }

}
