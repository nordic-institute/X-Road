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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;

import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceDescription;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converter ServiceDescription related data between openapi and service domain classes
 */
@Component
public class ServiceDescriptionConverter {

    private final ClientConverter clientConverter;

    @Autowired
    public ServiceDescriptionConverter(ClientConverter clientConverter) {
        this.clientConverter = clientConverter;
    }

    /**
     * Converts a collection of ServiceDescriptionTypes to a list of ServiceDescriptions
     * @param serviceDescriptionTypes
     * @return
     */
    public List<ServiceDescription> convert(Collection<ServiceDescriptionType> serviceDescriptionTypes) {
        return serviceDescriptionTypes.stream()
                .map(this::convert).collect(Collectors.toList());
    }


    /**
     * Convert a ServiceDescriptionType into ServiceDescription.
     * Shallow convert for now, does not handle services.
     * @param serviceDescriptionType
     * @return
     */
    public ServiceDescription convert(ServiceDescriptionType serviceDescriptionType) {
        ServiceDescription serviceDescription = new ServiceDescription();

        serviceDescription.setClientId(clientConverter.convertId(
                serviceDescriptionType.getClient().getIdentifier()));
        serviceDescription.setDisabled(serviceDescriptionType.isDisabled());
        serviceDescription.setDisabledNotice(serviceDescriptionType.getDisabledNotice());
        serviceDescription.setRefreshedDate(FormatUtils.fromDateToOffsetDateTime(
                serviceDescriptionType.getRefreshedDate()));
        // TO DO: Shallow convert at the moment
        serviceDescription.setServices(new ArrayList<Service>());
        serviceDescription.setType(ServiceTypeMapping.map(serviceDescriptionType.getType()).get());
        serviceDescription.setUrl(serviceDescriptionType.getUrl());

        return serviceDescription;
    }


}
