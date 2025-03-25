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

import io.cucumber.java.en.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.niis.xroad.ss.test.ui.container.EnvSetup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

@Slf4j
public class SignerStepDefs extends BaseUiStepDefs {

    @SneakyThrows
    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("signer service is restarted")
    public void signerServiceIsRestarted() {
        envSetup.restartContainer("signer");
    }

    @SneakyThrows
    @Step("predefined signer softtoken is uploaded")
    @SuppressWarnings("checkstyle:MagicNumber")
    public void updateSignerSoftToken() {
        if (SystemUtils.IS_OS_UNIX) {
            envSetup.execInContainer(EnvSetup.SIGNER, "chmod", "-R", "777", "/etc/xroad/signer");
        }

        envSetup.stop(EnvSetup.SIGNER);

        FileUtils.deleteDirectory(Paths.get("build/signer-volume").toFile());
        FileUtils.copyDirectory(
                Paths.get("src/intTest/resources/container-files/etc/xroad/signer-predefined").toFile(),
                Paths.get("build/signer-volume").toFile());

        envSetup.start(EnvSetup.SIGNER);
    }

    @Step("Predefined inactive signer token is uploaded")
    public void addInactiveSignerToken() throws Exception {
        // make file accessible for editing outside the container.
        if (SystemUtils.IS_OS_UNIX) {
            envSetup.execInContainer(EnvSetup.SIGNER, "chmod", "-R", "777", "/etc/xroad/signer");
        }
        String deviceEntry = """
                </device>
                <device>
                    <deviceType>softToken</deviceType>
                    <friendlyName>softToken-for-deletion</friendlyName>
                    <id>1</id>
                    <pinIndex>1</pinIndex>
                </device>
                """;

        try {
            String currenKeyconf = envSetup
                    .execInContainer(EnvSetup.SIGNER, "cat", "/etc/xroad/signer/keyconf.xml").getStdout();

            envSetup.stop(EnvSetup.SIGNER);

            String updatedKeyConf = currenKeyconf.replaceFirst("</device>", deviceEntry);

            Path tempDir = Files.createTempDirectory("signertmp");
            FileUtils.copyDirectory(Paths.get("build/signer-volume/softtoken").toFile(),
                    tempDir.resolve("softtoken").toFile());

            Path keyconfFile = tempDir.resolve("keyconf.xml");
            Files.writeString(keyconfFile, updatedKeyConf);

            FileUtils.deleteDirectory(Paths.get("build/signer-volume").toFile());
            FileUtils.copyDirectory(
                    tempDir.toFile(),
                    Paths.get("build/signer-volume").toFile());

//            logFileInfo(keyconfPath);
//            logFileInfo(Paths.get("build/signer-volume"));
//
//            String content = Files.readString(keyconfPath);
//            content = content.replaceFirst("</device>", deviceEntry);
//            Files.writeString(keyconfPath, content);
        } catch (Exception e) {
            log.error("Failed to modify keyconf.xml file", e);
            throw e;
//            testReportService.attachText(, e.getMessage());
        } finally {
            envSetup.start(EnvSetup.SIGNER);
        }

    }

    private void logFileInfo(Path path) throws IOException {
        PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class);
        log.info("----------------");
        log.info(path.toString());
        log.info("Owner: " + attrs.owner().getName());
        log.info("Group: " + attrs.group().getName());

        Set<PosixFilePermission> permissions = attrs.permissions();
        log.info("Permissions: " + PosixFilePermissions.toString(permissions));
        log.info("================");
    }

}
