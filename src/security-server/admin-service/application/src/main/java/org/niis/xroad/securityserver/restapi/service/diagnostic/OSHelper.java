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
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;

import java.util.Collection;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class OSHelper {
    protected static final String PKG_LIST_SCRIPT_PATH = "/usr/share/xroad/scripts/list-installed-xrd-packages.sh";
    private static final Pattern XRD_JAR_PATTERN = Pattern.compile("/usr/share/xroad/jlib/(\\w+/)*([\\w.-]+\\.jar)");
    private static final String JAVA_PROCESS = "java";
    private static final String NAME_VERSION_SEPARATOR = "##";


    private final SystemInfo systemInfo;
    private final ExternalProcessRunner externalProcessRunner;

    public List<JavaProcess> getJavaProcesses() {
        return systemInfo.getOperatingSystem().getProcesses().stream()
                .filter(pr -> StringUtils.equalsIgnoreCase(pr.getName(), JAVA_PROCESS))
                .map(this::toJavaProcess)
                .toList();
    }

    public List<Package> getInstalledXRoadPackages() {

        try {
            var result = externalProcessRunner.executeAndThrowOnFailure(PKG_LIST_SCRIPT_PATH);
            if (result.getExitCode() != 0) {
                throw new OSException("Read failed with exit code: " + result.getExitCode());
            }

            return result.getProcessOutput().stream()
                    .map(line -> line.split(NAME_VERSION_SEPARATOR))
                    .filter(parts -> parts.length == 2)
                    .map(parts -> new Package(parts[0], parts[1]))
                    .toList();


        } catch (ProcessFailedException | ProcessNotExecutableException e) {
            throw new OSException("Read of installed X-Road packages failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OSException("Read of installed packages was interrupted", e);
        }
    }

    public OSDetails getOsDetails() {
        var os = systemInfo.getOperatingSystem();
        return new OSDetails(os.getFamily(), os.getVersionInfo().toString());
    }

    private JavaProcess toJavaProcess(OSProcess process) {
        var jars = process.getArguments().stream()
                .map(XRD_JAR_PATTERN::matcher)
                .flatMap(Matcher::results)
                .map(MatchResult::group)
                .toList();

        var args = process.getArguments().stream()
                .filter(arg -> StringUtils.startsWithIgnoreCase(arg, "-x"))
                .toList();
        return new JavaProcess(jars, args);
    }

    public record Package(String name, String version) {
    }

    public record JavaProcess(List<String> jars, List<String> args) {
    }

    public record PackagesDetails(Collection<Package> packages, Collection<String> unknownJars) {
    }

    public record OSDetails(String name, String version) {
    }

    public static class OSException extends RuntimeException {

        public OSException(String message) {
            super(message);
        }

        public OSException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
