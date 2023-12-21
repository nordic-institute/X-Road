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

import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.DnFieldValue;
import ee.ria.xroad.common.certificateprofile.impl.DnFieldValueImpl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_DN_PARAMETER;

/**
 * helper for working with DnFieldValues and -Descriptions
 */
@Slf4j
@Component
public class DnFieldHelper {

    /**
     * Transform dn fields into a subject name
     */
    public String createSubjectName(List<DnFieldValue> dnFieldValues) {
        return dnFieldValues.stream()
                .filter(dnFieldValue -> !StringUtils.isBlank(dnFieldValue.getValue()))
                .map(dnFieldValue -> dnFieldValue.getId() + "=" + dnFieldValue.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Read dn parameters from dnParameters map, match them to DnFieldDescription
     * definitions (consider readOnly, required, etc) and validate that all parameters
     * are fine.
     * @return valid DnFieldValue objects
     * @throws InvalidDnParameterException if there were invalid parameters
     */
    public List<DnFieldValue> processDnParameters(CertificateProfileInfo profile, Map<String, String> dnParameters)
            throws InvalidDnParameterException {
        Set<String> unprocessedParameters = new HashSet<>(dnParameters.keySet());
        List<DnFieldValue> dnValues = new ArrayList<>();
        // match all dn fields with either default values or actual parameters
        for (DnFieldDescription description: profile.getSubjectFields()) {
            String fieldValue = null;
            boolean parameterIsMissing = StringUtils.isBlank(dnParameters.get(description.getId()));
            if (description.isRequired() && (!description.isReadOnly()) && parameterIsMissing) {
                throw new InvalidDnParameterException("missing parameter: " + description.getId());
            }
            if (description.isReadOnly() || parameterIsMissing) {
                fieldValue = description.getDefaultValue();
            } else {
                fieldValue = dnParameters.get(description.getId());
            }
            dnValues.add(new DnFieldValueImpl(description.getId(), fieldValue));
            unprocessedParameters.remove(description.getId());
        }
        if (!unprocessedParameters.isEmpty()) {
            throw new InvalidDnParameterException("extraneous parameters: " + unprocessedParameters);
        }
        // validate
        for (DnFieldValue dnValue: dnValues) {
            try {
                profile.validateSubjectField(dnValue);
            } catch (Exception e) {
                throw new InvalidDnParameterException(e);
            }
        }
        return dnValues;
    }

    /**
     * Thrown if a subject dn parameter was invalid
     */
    public static class InvalidDnParameterException extends ServiceException {
        public InvalidDnParameterException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_INVALID_DN_PARAMETER));
        }
        public InvalidDnParameterException(String s) {
            super(s, new ErrorDeviation(ERROR_INVALID_DN_PARAMETER));
        }
    }
}
