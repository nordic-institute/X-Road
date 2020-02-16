/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.TspType;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.openapi.model.TimestampingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Service that handles timestamping services
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class TimestampingServiceService {

    private final GlobalConfService globalConfService;
    private final ServerConfService serverConfService;

    /**
     * constructor
     */
    @Autowired
    public TimestampingServiceService(GlobalConfService globalConfService, ServerConfService serverConfService) {
        this.globalConfService = globalConfService;
        this.serverConfService = serverConfService;
    }

    /**
     * Return approved timestamping authorities
     * @return
     */
    public Collection<String> getTimestampingServices() {
        return globalConfService.getApprovedTspsForThisInstance();
    }

    /**
     * Deletes a configured timestamping service from serverconf
     * @param timestampingService
     * @throws TimestampingServiceNotFoundException
     */
    public void deleteConfiguredTimestampingService(TimestampingService timestampingService)
            throws TimestampingServiceNotFoundException {
        List<TspType> configuredTimestampingServices = serverConfService.getConfiguredTimestampingServices();
        TspType delete = null;

        for (TspType tsp: configuredTimestampingServices) {
            if (timestampingService.getName().equals(tsp.getName())
                    && timestampingService.getUrl().equals(tsp.getUrl())) {
                delete = tsp;
            }
        }
        if (delete == null) {
            throw new TimestampingServiceNotFoundException("Timestamping service with name "
                    + timestampingService.getName() + " and url " + timestampingService.getUrl() + " not found");
        }
        configuredTimestampingServices.remove(delete);
    }
}
