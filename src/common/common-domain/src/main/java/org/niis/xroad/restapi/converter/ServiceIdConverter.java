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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.restapi.util.FormatUtils;

import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.identifier.XRoadId.ENCODED_ID_SEPARATOR;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_ENCODED_ID;

/**
 * Converter for encoded service ids
 */
@Named("serviceIdConverter")
@RequiredArgsConstructor
public class ServiceIdConverter extends DtoConverter<ServiceId, String> {

    /**
     * Encoded service id consists of <encoded client id>:<full service code>
     * Separator ':' is Converters.ENCODED_ID_SEPARATOR
     */
    public static final int FULL_SERVICE_CODE_INDEX = 4;

    private final ClientIdConverter clientIdConverter = new ClientIdConverter();

    public ServiceId convertId(String encodedId) {
        validateEncodedString(encodedId);
        String fullServiceCode = parseFullServiceCode(encodedId);
        String[] parts = fullServiceCode.split("\\.");
        if (parts.length == 2) {
            return ServiceId.Conf.create(parseClientId(encodedId), parts[0], parts[1]);
        } else if (parts.length == 1) {
            return ServiceId.Conf.create(parseClientId(encodedId), parts[0]);
        } else {
            throw new BadRequestException("Invalid service id " + encodedId, INVALID_ENCODED_ID.build());
        }
    }

    public ClientId parseClientId(String encodedId) {
        validateEncodedString(encodedId);
        String encodedClientId = encodedId.substring(0, encodedId.lastIndexOf(ENCODED_ID_SEPARATOR));
        return clientIdConverter.convertId(encodedClientId);
    }

    public String parseFullServiceCode(String encodedId) {
        validateEncodedString(encodedId);
        List<String> parts = Arrays.asList(encodedId.split(String.valueOf(ENCODED_ID_SEPARATOR)));
        return parts.getLast();
    }

    private void validateEncodedString(String encodedId) {
        int separators = FormatUtils.countOccurences(encodedId, ENCODED_ID_SEPARATOR);
        if (separators != FULL_SERVICE_CODE_INDEX) {
            throw new BadRequestException("Invalid service id " + encodedId, INVALID_ENCODED_ID.build());
        }
    }

    public String convertId(ServiceId serviceId) {
        String encodedId = serviceId.asEncodedId(false);
        if (serviceId.getServiceVersion() != null) {
            int lastIndex = encodedId.lastIndexOf(ENCODED_ID_SEPARATOR);
            return encodedId.substring(0, lastIndex) + "." + encodedId.substring(lastIndex + 1);
        }
        return encodedId;
    }

    @Override
    public ServiceId fromDto(String source) {
        return convertId(source);
    }

    @Override
    public String toDto(ServiceId source) {
        return convertId(source);
    }
}
