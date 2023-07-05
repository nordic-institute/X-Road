/**
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.Version;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.dto.VersionInfoDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import static ee.ria.xroad.common.Version.MAX_SUPPORTED_JAVA_VERSION;
import static ee.ria.xroad.common.Version.MIN_SUPPORTED_JAVA_VERSION;

/**
 * service class for handling X-Road version information
 */
@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
public class VersionService {


    /**
     * Returns X-Road software version number and java version information
     * @return
     */
    public VersionInfoDto getVersionInfo() {
        VersionInfoDto result = new VersionInfoDto();
        result.setInfo(Version.XROAD_VERSION);
        int javaVersion = Version.readJavaVersion();
        result.setJavaVersion(javaVersion);
        result.setMinJavaVersion(MIN_SUPPORTED_JAVA_VERSION);
        result.setMaxJavaVersion(MAX_SUPPORTED_JAVA_VERSION);
        result.setUsingSupportedJavaVersion(javaVersion >= MIN_SUPPORTED_JAVA_VERSION
                && javaVersion <= MAX_SUPPORTED_JAVA_VERSION);
        result.setJavaVendor(Version.JAVA_VENDOR);
        result.setJavaRuntimeVersion(Version.JAVA_RUNTIME_VERSION);

        return result;
    }
}
