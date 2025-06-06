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

package org.niis.xroad.cs.admin.core.validation;

import ee.ria.xroad.common.CodedException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.globalconf.extension.OcspFetchIntervalSchemaValidator;
import org.niis.xroad.globalconf.extension.OcspNextUpdateSchemaValidator;
import org.niis.xroad.globalconf.monitoringconf.MonitoringParametersSchemaValidator;

import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.CONTENT_ID_MONITORING;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.CONTENT_ID_OCSP_FETCH_INTERVAL;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.CONTENT_ID_OCSP_NEXT_UPDATE;

class ConfigurationPartValidatorTest {

    private static final byte[] FILE_DATA = UUID.randomUUID().toString().getBytes(UTF_8);

    private final ConfigurationPartValidator configurationPartValidator = new ConfigurationPartValidator();

    @Test
    void testValidateMonitoring() {
        try (MockedStatic<MonitoringParametersSchemaValidator> schemaValidator =
                     mockStatic(MonitoringParametersSchemaValidator.class)) {
            configurationPartValidator.validate(CONTENT_ID_MONITORING, FILE_DATA);

            schemaValidator.verify(() -> MonitoringParametersSchemaValidator.validate(new String(FILE_DATA, UTF_8)));
        }
    }

    @Test
    void testValidateMonitoringFails() {
        try (MockedStatic<MonitoringParametersSchemaValidator> schemaValidator =
                     mockStatic(MonitoringParametersSchemaValidator.class)) {

            schemaValidator.when(() -> MonitoringParametersSchemaValidator.validate(new String(FILE_DATA, UTF_8)))
                    .thenThrow(new CodedException("code"));

            assertThatThrownBy(() -> configurationPartValidator.validate(CONTENT_ID_MONITORING, FILE_DATA))
                    .isExactlyInstanceOf(BadRequestException.class)
                    .hasMessage("Error[code=configuration_part_validation_failed]");
        }
    }

    @Test
    void testValidateOcspFetchInterval() {
        try (MockedStatic<OcspFetchIntervalSchemaValidator> schemaValidator =
                     mockStatic(OcspFetchIntervalSchemaValidator.class)) {

            configurationPartValidator.validate(CONTENT_ID_OCSP_FETCH_INTERVAL, FILE_DATA);

            schemaValidator.verify(() -> OcspFetchIntervalSchemaValidator.validate(new String(FILE_DATA, UTF_8)));
        }
    }

    @Test
    void testValidateOcspFetchIntervalFails() {
        try (MockedStatic<OcspFetchIntervalSchemaValidator> schemaValidator =
                     mockStatic(OcspFetchIntervalSchemaValidator.class)) {

            schemaValidator.when(() -> OcspFetchIntervalSchemaValidator.validate(new String(FILE_DATA, UTF_8)))
                    .thenThrow(new CodedException("code"));

            assertThatThrownBy(() -> configurationPartValidator.validate(CONTENT_ID_MONITORING, FILE_DATA))
                    .isExactlyInstanceOf(BadRequestException.class)
                    .hasMessage("Error[code=configuration_part_validation_failed]");
        }
    }

    @Test
    void validateOcspNextUpdate() {
        try (MockedStatic<OcspNextUpdateSchemaValidator> schemaValidator =
                     mockStatic(OcspNextUpdateSchemaValidator.class)) {
            configurationPartValidator.validate(CONTENT_ID_OCSP_NEXT_UPDATE, FILE_DATA);

            schemaValidator.verify(() -> OcspNextUpdateSchemaValidator.validate(new String(FILE_DATA, UTF_8)));
        }
    }

    @Test
    void validateOcspNextUpdateFails() {
        try (MockedStatic<OcspNextUpdateSchemaValidator> schemaValidator =
                     mockStatic(OcspNextUpdateSchemaValidator.class)) {

            schemaValidator.when(() -> OcspNextUpdateSchemaValidator.validate(new String(FILE_DATA, UTF_8)))
                    .thenThrow(new CodedException("code"));

            assertThatThrownBy(() -> configurationPartValidator.validate(CONTENT_ID_MONITORING, FILE_DATA))
                    .isExactlyInstanceOf(BadRequestException.class)
                    .hasMessage("Error[code=configuration_part_validation_failed]");
        }
    }

    @Test
    void validateUnknownPartShouldFail() {
        assertThatThrownBy(() -> configurationPartValidator.validate("UNKNOWN-CONTENT-ID", FILE_DATA))
                .isExactlyInstanceOf(BadRequestException.class)
                .hasMessage("Error[code=configuration_part_validation_failed]");
    }

}
