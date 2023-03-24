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

import ee.ria.xroad.common.conf.globalconfextension.OcspFetchIntervalSchemaValidator;
import ee.ria.xroad.common.conf.globalconfextension.OcspNextUpdateSchemaValidator;
import ee.ria.xroad.common.conf.monitoringconf.MonitoringParametersSchemaValidator;

import org.niis.xroad.common.exception.ValidationFailureException;
import org.springframework.stereotype.Component;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_MONITORING;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_OCSP_FETCH_INTERVAL;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_OCSP_NEXT_UPDATE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CONFIGURATION_PART_VALIDATION_FAILED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CONFIGURATION_PART_VALIDATOR_NOT_FOUND;

@Component
public class ConfigurationPartValidator {

    public void validate(String contentIdentifier, byte[] data) {
        final String content = new String(data, UTF_8);
        try {
            switch (contentIdentifier) {
                case CONTENT_ID_MONITORING:
                    MonitoringParametersSchemaValidator.validate(content);
                    break;
                case CONTENT_ID_OCSP_FETCH_INTERVAL:
                    OcspFetchIntervalSchemaValidator.validate(content);
                    break;
                case CONTENT_ID_OCSP_NEXT_UPDATE:
                    OcspNextUpdateSchemaValidator.validate(content);
                    break;
                default:
                    throw new ValidationFailureException(CONFIGURATION_PART_VALIDATOR_NOT_FOUND);
            }
        } catch (Exception e) {
            throw new ValidationFailureException(CONFIGURATION_PART_VALIDATION_FAILED, e);
        }
    }

}
