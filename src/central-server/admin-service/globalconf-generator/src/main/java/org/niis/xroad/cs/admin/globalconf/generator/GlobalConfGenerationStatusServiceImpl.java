/*
 * The MIT License
 *
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

package org.niis.xroad.cs.admin.globalconf.generator;

import ee.ria.xroad.common.SystemProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus;
import org.niis.xroad.cs.admin.api.service.GlobalConfGenerationStatusService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

import static ee.ria.xroad.common.util.TimeUtils.now;
import static org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.FAILURE;
import static org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.SUCCESS;
import static org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.UNKNOWN;

@Component
@RequiredArgsConstructor
@Slf4j
public class GlobalConfGenerationStatusServiceImpl implements GlobalConfGenerationStatusService {

    static final String STATUS_FILE_NAME = ".global_conf_gen_status";

    private final ObjectMapper objectMapper;

    @EventListener
    public void handleGlobalConfGenerationEvent(GlobalConfGenerationEvent event) {
        if (event == GlobalConfGenerationEvent.SUCCESS) {
            saveSuccess();
        } else {
            saveFailure();
        }
    }

    private void saveSuccess() {
        writeFile(GlobalConfGenerationStatusInternal.builder().success(true).time(now()).build());
    }

    private void saveFailure() {
        final GlobalConfGenerationStatus lastStatus = get();

        if (lastStatus.getStatus() != FAILURE) {
            writeFile(GlobalConfGenerationStatusInternal.builder().time(now()).success(false).build());
        }
    }

    private void writeFile(GlobalConfGenerationStatusInternal status) {
        try {
            Files.writeString(Paths.get(SystemProperties.getLogPath(), STATUS_FILE_NAME),
                    objectMapper.writeValueAsString(status));
        } catch (Exception e) {
            log.warn("Failed to write global conf generation status file", e);
        }
    }

    @Override
    public GlobalConfGenerationStatus get() {
        try {
            final String content = Files.readString(Paths.get(SystemProperties.getLogPath(), STATUS_FILE_NAME));
            final GlobalConfGenerationStatusInternal statusInternal =
                    objectMapper.readValue(content, GlobalConfGenerationStatusInternal.class);
            return new GlobalConfGenerationStatus(statusInternal.isSuccess() ? SUCCESS : FAILURE, statusInternal.getTime());
        } catch (Exception e) {
            log.warn("Failed to read global conf generation status file", e);
            return new GlobalConfGenerationStatus(UNKNOWN, null);
        }
    }

    @Value
    @Jacksonized
    @Builder
    private static class GlobalConfGenerationStatusInternal {
        Instant time;
        boolean success;
    }

}
