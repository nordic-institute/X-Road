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
package org.niis.xroad.cs.admin.globalconf.generator;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.cs.admin.api.service.config.GlobalConfigDirectories;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static ee.ria.xroad.common.GlobalConfVersion.CURRENT_VERSION;
import static ee.ria.xroad.common.GlobalConfVersion.MINIMUM_SUPPORTED_VERSION;

@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "xroad.admin-service.global-conf-generator")
@Getter
@Setter

public class GlobalConfGenerationProperties implements GlobalConfigDirectories {
    private String internalDirectory;
    private String externalDirectory;
    private String generatedConfDir;
    @Min(1)
    @Max(CURRENT_VERSION)
    private int minimumGlobalConfigurationVersion;

    /**
     * Get the minimum global configuration version, but never lower than the minimum supported version.
     * @return minimum global configuration version
     */
    public int getMinimumGlobalConfigurationVersion() {
        return Integer.max(minimumGlobalConfigurationVersion, MINIMUM_SUPPORTED_VERSION);
    }

    public String getTmpInternalDirectory() {
        return internalDirectory + ".tmp";
    }

    public String getTmpExternalDirectory() {
        return externalDirectory + ".tmp";
    }

}
