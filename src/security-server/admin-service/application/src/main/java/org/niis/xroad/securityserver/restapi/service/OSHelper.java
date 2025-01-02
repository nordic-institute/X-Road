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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OSHelper {
    private static final Map<String, String> JAR2PACKAGE;

    private static final Pattern XRD_JAR_PATTERN = Pattern.compile("/usr/share/xroad/jlib/(\\w+/)*(?<name>[\\w.-]+\\.jar)");
    private static final String JAR_GROUP_NAME = "name";
    private static final String JAVA_PROCESS = "java";
    private static final String PKG_LIST_SCRIPT_PATH = "/usr/share/xroad/scripts/list-installed-xrd-packages.sh";
    private static final String NAME_VERSION_SEPARATOR = "##";

    static {
        var map = new HashMap<String, String>();
        map.put("hwtoken-1.0.jar", "xroad-addon-hwtokens");

        map.put("messagelog-archiver.jar", "xroad-addon-messagelog");
        map.put("messagelog-archive-verifier.jar", "xroad-addon-messagelog");
        map.put("asicverifier.jar", "xroad-addon-messagelog");
        map.put("messagelog-addon.jar", "xroad-addon-messagelog");

        map.put("metaservice-1.0.jar", "xroad-addon-metaservices");

        map.put("op-monitoring-1.0.jar", "xroad-addon-opmonitoring");

        map.put("proxymonitor-metaservice-1.0.jar", "xroad-addon-proxymonitor");

        map.put("wsdlvalidator-1.0.jar", "xroad-addon-wsdlvalidator");

        map.put("common-db.jar", "xroad-base");

        map.put("configuration-client.jar", "xroad-confclient");

        map.put("configuration-proxy.jar", "xroad-confproxy");

        map.put("monitor.jar", "xroad-monitor");

        map.put("op-monitor-daemon.jar", "xroad-opmonitor");

        map.put("proxy.jar", "xroad-proxy");

        map.put("proxy-ui-api.jar", "xroad-proxy-ui-api");

        map.put("signer.jar", "xroad-signer");
        map.put("signer-console.jar", "xroad-signer");

        JAR2PACKAGE = Map.copyOf(map);
    }

    private final SystemInfo systemInfo = new SystemInfo();
    private final ExternalProcessRunner externalProcessRunner;

    public PackagesDetails getXRoadPackagesDetails() {
        var packages = getInstalledXRoadPackages();

        var unknownJars = new HashSet<String>();
        var packagesWithJars = packages.stream()
                .collect(Collectors.toMap(Package::getName, Function.identity()));

        systemInfo.getOperatingSystem().getProcesses().stream()
                .filter(pr -> StringUtils.equalsIgnoreCase(pr.getName(), JAVA_PROCESS))
                .map(OSProcess::getArguments)
                .flatMap(List::stream)
                .map(XRD_JAR_PATTERN::matcher)
                .flatMap(Matcher::results)
                .forEach(res -> {
                    var packageName = JAR2PACKAGE.get(res.group(JAR_GROUP_NAME));
                    var xrdPackage = packageName == null ? null : packagesWithJars.get(packageName);
                    if (xrdPackage == null) {
                        unknownJars.add(res.group());
                    } else {
                        xrdPackage.addJar(res.group());
                    }
                });

        return new PackagesDetails(packagesWithJars.values(), unknownJars);
    }

    private List<Package> getInstalledXRoadPackages() {

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

    @RequiredArgsConstructor
    @Getter
    public class Package {

        private final String name;
        private final String version;
        private List<String> runningJars;

        public void addJar(String jar) {
            if (runningJars == null) {
                runningJars = new LinkedList<>();
            }
            runningJars.add(jar);
        }
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
