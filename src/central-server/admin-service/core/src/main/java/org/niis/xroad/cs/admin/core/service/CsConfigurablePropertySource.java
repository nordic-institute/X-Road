/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package org.niis.xroad.cs.admin.core.service;

import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.keys.CsAdminServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CsManagementServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CsRegistrationServiceConfigKeys;
import org.niis.xroad.restapi.service.ConfigurablePropertySource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Central Server's {@link ConfigurablePropertySource}: the providers whose exposed keys make up the
 * Central Server's system-parameters catalogue, and their category-to-scope mapping.
 */
@Component
public class CsConfigurablePropertySource implements ConfigurablePropertySource {

    private static final List<ConfigKeyProvider> CS_PROVIDERS = List.of(
            CsAdminServiceConfigKeys.instance(),
            CsManagementServiceConfigKeys.instance(),
            CsRegistrationServiceConfigKeys.instance());

    @Override
    public List<ConfigKeyProvider> getConfigKeyProviders() {
        return CS_PROVIDERS;
    }

    @Override
    public String categoryToScope(Category category) {
        return switch (category) {
            case ADMIN_SERVICE -> "admin-service";
            case MANAGEMENT_SERVICE -> "management-service";
            case REGISTRATION_SERVICE -> "registration-service";
            case COMMON -> null;
            default -> throw XrdRuntimeException.systemInternalError(
                    "Unmapped category for configurable properties catalogue: " + category);
        };
    }
}
