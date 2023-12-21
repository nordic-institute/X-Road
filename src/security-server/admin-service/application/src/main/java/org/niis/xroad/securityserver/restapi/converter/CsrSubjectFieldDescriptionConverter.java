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

import ee.ria.xroad.common.certificateprofile.DnFieldDescription;

import com.google.common.collect.Streams;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrSubjectFieldDescription;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converter for CsrSubjectFieldDescription related data between openapi and service domain classes
 */
@Component
public class CsrSubjectFieldDescriptionConverter {

    /**
     * convert DnFieldDescription into openapi CsrSubjectFieldDescription class
     * @param dnFieldDescription
     * @return
     */
    public CsrSubjectFieldDescription convert(DnFieldDescription dnFieldDescription) {
        CsrSubjectFieldDescription description = new CsrSubjectFieldDescription();
        description.setId(dnFieldDescription.getId());
        if (dnFieldDescription.isLocalized()) {
            description.setLabelKey(dnFieldDescription.getLabelKey());
        } else {
            description.setLabel(dnFieldDescription.getLabel());
        }
        description.setLocalized(dnFieldDescription.isLocalized());
        description.setDefaultValue(dnFieldDescription.getDefaultValue());
        description.setReadOnly(dnFieldDescription.isReadOnly());
        description.setRequired(dnFieldDescription.isRequired());
        return description;
    }

    /**
     * convert a group of DnFieldDescriptions into a list of CsrSubjectFieldDescriptions
     * @param dnFieldDescriptions
     * @return
     */
    public Set<CsrSubjectFieldDescription> convert(Iterable<DnFieldDescription> dnFieldDescriptions) {
        return Streams.stream(dnFieldDescriptions)
                .map(this::convert)
                .collect(Collectors.toSet());
    }
    /**
     * convert an array of DnFieldDescriptions into a list of CsrSubjectFieldDescriptions
     * @param dnFieldDescriptions
     * @return
     */
    public Set<CsrSubjectFieldDescription> convert(DnFieldDescription[] dnFieldDescriptions) {
        return convert(new HashSet<>(Arrays.asList(dnFieldDescriptions)));
    }
}
