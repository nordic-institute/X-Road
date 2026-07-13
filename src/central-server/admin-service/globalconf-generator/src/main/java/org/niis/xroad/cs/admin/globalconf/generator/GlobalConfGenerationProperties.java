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

import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CsAdminServiceConfigKeys;
import org.niis.xroad.cs.admin.api.service.config.GlobalConfigDirectories;

import static ee.ria.xroad.common.GlobalConfVersion.MINIMUM_SUPPORTED_VERSION;

/**
 * Global configuration generation directories and version floor ({@code xroad.admin-service.global-conf-generator.*}),
 * resolved through {@link XRoadConfig}.
 */
public class GlobalConfGenerationProperties implements GlobalConfigDirectories {

    private final XRoadConfig config;

    public GlobalConfGenerationProperties(XRoadConfig config) {
        this.config = config;
    }

    @Override
    public String getInternalDirectory() {
        return config.value(CsAdminServiceConfigKeys.GLOBAL_CONF_GENERATOR_INTERNAL_DIRECTORY);
    }

    @Override
    public String getExternalDirectory() {
        return config.value(CsAdminServiceConfigKeys.GLOBAL_CONF_GENERATOR_EXTERNAL_DIRECTORY);
    }

    public String getGeneratedConfDir() {
        return config.value(CsAdminServiceConfigKeys.GLOBAL_CONF_GENERATOR_GENERATED_CONF_DIR);
    }

    /**
     * @return the minimum global configuration version, but never lower than the minimum supported version
     */
    public int getMinimumGlobalConfigurationVersion() {
        return Integer.max(config.value(CsAdminServiceConfigKeys.GLOBAL_CONF_GENERATOR_MINIMUM_GLOBAL_CONFIGURATION_VERSION),
                MINIMUM_SUPPORTED_VERSION);
    }

    public String getTmpInternalDirectory() {
        return getInternalDirectory() + ".tmp";
    }

    public String getTmpExternalDirectory() {
        return getExternalDirectory() + ".tmp";
    }
}
