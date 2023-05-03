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

package org.niis.xroad.cs.admin.globalconf.generator;

import ee.ria.xroad.common.SystemProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfGenerationEvent.FAILURE;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfGenerationEvent.SUCCESS;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfGenerationStatusServiceImpl.STATUS_FILE_NAME;

class GlobalConfGenerationStatusServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    private final GlobalConfGenerationStatusServiceImpl globalConfGenerationStatusService =
            new GlobalConfGenerationStatusServiceImpl(OBJECT_MAPPER);

    private static String currentLogPath;

    @BeforeAll
    public static void saveSystemProperty() {
        currentLogPath = System.getProperty(SystemProperties.LOG_PATH);
        System.setProperty(SystemProperties.LOG_PATH, System.getProperty("java.io.tmpdir"));
    }

    @AfterAll
    public static void restoreSystemProperty() {
        FileUtils.deleteQuietly(Paths.get(SystemProperties.getLogPath(), STATUS_FILE_NAME).toFile());
        if (currentLogPath == null) {
            System.clearProperty(SystemProperties.LOG_PATH);
        } else {
            System.setProperty(SystemProperties.LOG_PATH, currentLogPath);
        }
    }

    @BeforeEach
    public void deleteStatusFileIfExists() {
        FileUtils.deleteQuietly(Paths.get(SystemProperties.getLogPath(), STATUS_FILE_NAME).toFile());
    }

    @Test
    void success() {
        globalConfGenerationStatusService.handleGlobalConfGenerationEvent(SUCCESS);
        var status = globalConfGenerationStatusService.get();
        assertThat(status.getStatus()).isEqualTo(GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.SUCCESS);
        assertThat(status.getTime()).isNotNull();
    }

    @Test
    @SneakyThrows
    @SuppressWarnings("java:S2925") // ignore Thread.sleep() rule on Sonar
    void timestampUpdatedOnConsecutiveSuccesses() {
        globalConfGenerationStatusService.handleGlobalConfGenerationEvent(SUCCESS);
        var firstStatus = globalConfGenerationStatusService.get();

        globalConfGenerationStatusService.handleGlobalConfGenerationEvent(SUCCESS);
        Thread.sleep(100); //make sure timestamp is different
        globalConfGenerationStatusService.handleGlobalConfGenerationEvent(SUCCESS);

        var latestStatus = globalConfGenerationStatusService.get();

        assertThat(latestStatus.getTime()).isAfter(firstStatus.getTime());
        assertThat(latestStatus.getStatus()).isEqualTo(GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.SUCCESS);
    }

    @Test
    @SneakyThrows
    @SuppressWarnings("java:S2925") // ignore Thread.sleep() rule on Sonar
    void timestampNotUpdatedOnConsecutiveFailures() {
        globalConfGenerationStatusService.handleGlobalConfGenerationEvent(FAILURE);
        var firstStatus = globalConfGenerationStatusService.get();

        globalConfGenerationStatusService.handleGlobalConfGenerationEvent(FAILURE);
        Thread.sleep(100); //make sure timestamp is different
        globalConfGenerationStatusService.handleGlobalConfGenerationEvent(FAILURE);

        var latestStatus = globalConfGenerationStatusService.get();

        assertThat(latestStatus.getTime()).isEqualTo(firstStatus.getTime());
        assertThat(latestStatus.getStatus()).isEqualTo(GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.FAILURE);
    }

    @Test
    void unknownGlobalConfGenerationStatus() {
        var status = globalConfGenerationStatusService.get();

        assertThat(status.getStatus()).isEqualTo(GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.UNKNOWN);
        assertThat(status.getTime()).isNull();
    }

}
