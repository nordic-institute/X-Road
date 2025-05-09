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
package org.niis.xroad.securityserver.restapi.converter;

import org.niis.xroad.securityserver.restapi.dto.MaintenanceMode;
import org.niis.xroad.securityserver.restapi.openapi.model.MaintenanceModeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MaintenanceModeStatusDto;
import org.springframework.stereotype.Component;

/**
 * Converter for AlertData related data between openapi and service domain classes
 */
@Component
public class MaintenanceModeConverter {

    public MaintenanceModeDto convert(MaintenanceMode maintenanceMode) {
        var target = new MaintenanceModeDto();
        target.setMessage(maintenanceMode.message());
        target.setStatus(switch (maintenanceMode.status()) {
            case ENABLING -> MaintenanceModeStatusDto.PENDING_ENABLE_MAINTENANCE_MODE;
            case ENABLED -> MaintenanceModeStatusDto.ENABLED_MAINTENANCE_MODE;
            case DISABLING -> MaintenanceModeStatusDto.PENDING_DISABLE_MAINTENANCE_MODE;
            case DISABLED -> MaintenanceModeStatusDto.DISABLED_MAINTENANCE_MODE;
        });

        return target;
    }
}
