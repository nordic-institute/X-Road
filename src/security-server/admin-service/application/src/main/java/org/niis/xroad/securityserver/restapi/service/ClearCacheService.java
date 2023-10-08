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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.PortNumbers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Service for clearing proxy cache.
 */
@Slf4j
@Service
public class ClearCacheService {
    private static final int REST_TEMPLATE_TIMEOUT_MS = 5000;

    private final RestTemplate restTemplate;
    private final String clearConfCacheUrl;

    @Autowired
    public ClearCacheService(@Value("${url.clear-configuration-cache}") String clearConfCacheUrl,
                             RestTemplateBuilder restTemplateBuilder) {
        this.clearConfCacheUrl = String.format(clearConfCacheUrl, PortNumbers.ADMIN_PORT);
        this.restTemplate = restTemplateBuilder
                .setReadTimeout(Duration.ofMillis(REST_TEMPLATE_TIMEOUT_MS))
                .build();
    }

    // used for testing
    ClearCacheService() {
        clearConfCacheUrl = null;
        restTemplate = null;
    }

    /**
     * Sends an http request to proxy in order to trigger the clearing (invalidating) of cache.
     * @return true if clearing succeeded, false otherwise (error is logged)
     */
    public boolean executeClearConfigurationCache() {
        log.info("Starting to clear configuration cache {}", clearConfCacheUrl);
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.getForEntity(clearConfCacheUrl, String.class);
        } catch (Exception e) {
            log.error("Clearing cache failed", e);
            return false;
        }
        if (response != null && response.getStatusCode() != HttpStatus.OK) {
            log.error("Clearing cache failed, HttpStatus={}", response.getStatusCode().value());
            return false;
        }
        return true;
    }
}
