/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.identifier.XRoadId.ENCODED_ID_SEPARATOR;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_ENCODED_ID;

/**
 * Converter for encoded client ids
 */
@Service
public class ClientIdConverter extends AbstractConverter<ClientId, String> {

    public static final int INSTANCE_INDEX = 0;
    public static final int MEMBER_CLASS_INDEX = 1;
    public static final int MEMBER_CODE_INDEX = 2;
    public static final int SUBSYSTEM_CODE_INDEX = 3;

    /**
     * Convert ClientId into encoded member id
     *
     * @return
     */
    public String convertId(ClientId clientId) {
        return convertId(clientId, false);
    }

    /**
     * Convert ClientId into encoded member id
     *
     * @param clientId
     * @return
     */
    public String convertId(ClientId clientId, boolean includeType) {
        return clientId.asEncodedId(includeType);
    }

    /**
     * Convert encoded member id into ClientId
     *
     * @param encodedId
     * @return ClientId
     * @throws ValidationFailureException if encoded id could not be decoded
     */
    public ClientId.Conf convertId(String encodedId) throws ValidationFailureException {
        if (!isEncodedClientId(encodedId)) {
            throw new ValidationFailureException(INVALID_ENCODED_ID, encodedId);
        }
        List<String> parts = Arrays.asList(encodedId.split(String.valueOf(ENCODED_ID_SEPARATOR)));
        String instance = parts.get(INSTANCE_INDEX);
        String memberClass = parts.get(MEMBER_CLASS_INDEX);
        String memberCode = parts.get(MEMBER_CODE_INDEX);
        String subsystemCode = null;
        if (parts.size() != (MEMBER_CODE_INDEX + 1)
                && parts.size() != (SUBSYSTEM_CODE_INDEX + 1)) {
            throw new ValidationFailureException(INVALID_ENCODED_ID, encodedId);
        }
        if (parts.size() == (SUBSYSTEM_CODE_INDEX + 1)) {
            subsystemCode = parts.get(SUBSYSTEM_CODE_INDEX);
        }
        return ClientId.Conf.create(instance, memberClass, memberCode, subsystemCode);
    }

    /**
     * Convert a list of encoded member ids to ClientIds
     *
     * @param encodedIds
     * @return List of ClientIds
     * @throws ValidationFailureException if encoded id could not be decoded
     */
    public List<ClientId> convertIds(List<String> encodedIds) throws ValidationFailureException {
        return encodedIds.stream().map(this::convertId).collect(Collectors.toList());
    }

    public boolean isEncodedSubsystemId(String encodedId) {
        int separators = FormatUtils.countOccurences(encodedId, ENCODED_ID_SEPARATOR);
        return separators == SUBSYSTEM_CODE_INDEX;
    }

    public boolean isEncodedMemberId(String encodedId) {
        int separators = FormatUtils.countOccurences(encodedId, ENCODED_ID_SEPARATOR);
        return separators == MEMBER_CODE_INDEX;
    }

    public boolean isEncodedClientId(String encodedId) {
        return isEncodedMemberId(encodedId) || isEncodedSubsystemId(encodedId);
    }

    @Override
    protected ClientId convertToA(String source) {
        return convertId(source);
    }

    @Override
    protected String convertToB(ClientId source) {
        return convertId(source);
    }
}
