/*
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.service.diagnostic;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OperatingSystem.OSVersionInfo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OSHelperTest {
    private static final String XRD_JAR1 = "/usr/share/xroad/jlib/proxy/proxy.jar";
    private static final String XRD_JAR2 = "/usr/share/xroad/jlib/signer.jar";
    private static final String VENDOR_JAR = "/usr/share/other/jlib/vendor.jar";
    private static final String ARG1 = "-Xmx512m";
    private static final String ARG2 = "-XX:MaxMetaspaceSize=200m";
    private static final String PKG_1_NAME = "xroad-base";
    private static final String PKG_1_VERSION = "7.6";
    private static final String PKG_2_NAME = "xroad-proxy";
    private static final String PKG_2_VERSION = "7.5";

    @Mock
    private SystemInfo systemInfo;
    @Mock
    private ExternalProcessRunner externalProcessRunner;
    @Mock
    private OperatingSystem operatingSystem;


    private OSHelper osHelper;

    @BeforeEach
    void setUp() {
        osHelper = new OSHelper(systemInfo, externalProcessRunner);
    }

    @Test
    void testGetJavaProcesses() {
        when(systemInfo.getOperatingSystem()).thenReturn(operatingSystem);

        var javaProcess = mock(OSProcess.class);
        when(javaProcess.getName()).thenReturn("java");
        when(javaProcess.getArguments()).thenReturn(List.of(
                "%s:%s:%s".formatted(XRD_JAR1, VENDOR_JAR, XRD_JAR2),
                ARG1,
                "-Dpassword=abc",
                ARG2
        ));

        var dbProcess = mock(OSProcess.class);
        when(dbProcess.getName()).thenReturn("db");

        when(operatingSystem.getProcesses()).thenReturn(List.of(javaProcess, dbProcess));

        var result = osHelper.getJavaProcesses();

        assertThat(result).size().isEqualTo(1);

        assertThat(result.getFirst().jars()).size().isEqualTo(2);
        assertThat(result.getFirst().jars()).contains(XRD_JAR1, XRD_JAR2);

        assertThat(result.getFirst().args()).size().isEqualTo(2);
        assertThat(result.getFirst().args()).contains(ARG1, ARG2);
    }

    @Test
    void testGetInstalledXRoadPackages() throws Exception {
        var processResult = mock(ExternalProcessRunner.ProcessResult.class);
        when(processResult.getExitCode()).thenReturn(0);
        when(processResult.getProcessOutput()).thenReturn(List.of(
                "%s##%s".formatted(PKG_1_NAME, PKG_1_VERSION),
                "%s##%s".formatted(PKG_2_NAME, PKG_2_VERSION)
        ));
        when(externalProcessRunner.executeAndThrowOnFailure(OSHelper.PKG_LIST_SCRIPT_PATH)).thenReturn(processResult);

        var packages = osHelper.getInstalledXRoadPackages();

        assertThat(packages).size().isEqualTo(2);
        assertThat(packages).contains(new OSHelper.Package(PKG_1_NAME, PKG_1_VERSION));
        assertThat(packages).contains(new OSHelper.Package(PKG_2_NAME, PKG_2_VERSION));
    }

    @Test
    void testGetInstalledXRoadPackagesFailure() throws Exception {

        when(externalProcessRunner.executeAndThrowOnFailure(anyString())).thenThrow(new ProcessFailedException("Failed"));

        assertThatThrownBy(() -> osHelper.getInstalledXRoadPackages())
                .isInstanceOf(OSHelper.OSException.class)
                .hasMessage("Read of installed X-Road packages failed");
    }

    @Test
    void testGetOsDetails() {
        final var version = "24.04";
        final var name = "Ubundu";
        var osVersionInfo = mock(OSVersionInfo.class);

        when(systemInfo.getOperatingSystem()).thenReturn(operatingSystem);

        when(operatingSystem.getFamily()).thenReturn(name);
        when(operatingSystem.getVersionInfo()).thenReturn(osVersionInfo);
        when(osVersionInfo.toString()).thenReturn(version);

        OSHelper.OSDetails details = osHelper.getOsDetails();

        assertThat(details.name()).isEqualTo(name);
        assertThat(details.version()).isEqualTo(version);
    }
}

