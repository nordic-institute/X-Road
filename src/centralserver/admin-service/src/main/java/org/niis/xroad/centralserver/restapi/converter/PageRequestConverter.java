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
package org.niis.xroad.centralserver.restapi.converter;

import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.centralserver.openapi.model.PagingSortingParameters;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PageRequestConverter {

    public PageRequest convert(PagingSortingParameters pagingSorting, SortParameterConverter sortParameterConverter) {
        return PageRequest.of(
                pagingSorting.getOffset(),
                pagingSorting.getLimit(),
                convertToSort(pagingSorting, sortParameterConverter));
    }

    private Sort convertToSort(PagingSortingParameters pagingSorting, SortParameterConverter sortParameterConverter) {
        var sort = Sort.unsorted();
        if (!StringUtils.isBlank(pagingSorting.getSort())) {
            Sort.Direction direction = Sort.Direction.ASC;
            if (Boolean.TRUE.equals(pagingSorting.getDesc())) {
                direction = Sort.Direction.DESC;
            }

            sort = Sort.by(new Sort.Order(direction,
                    sortParameterConverter.convertToSortProperty(pagingSorting.getSort()))
                    .ignoreCase());
        }
        return sort;
    }

    public interface SortParameterConverter {
        /**
         * Convert an API-level sort parameter to service-level property name that JPA Data can sort by
         *
         * @throws BadRequestException if parameter value cannot be converted
         */
        String convertToSortProperty(String sortParameter) throws BadRequestException;
    }

    /**
     * Mappable sort parameter converter. Allows mapping dto parameters to entity values.
     */
    public static class MappableSortParameterConverter implements SortParameterConverter {
        private final Map<String, String> conversionMapping;

        @SafeVarargs
        public MappableSortParameterConverter(final Map.Entry<String, String>... mappingEntries) {
            conversionMapping = Map.ofEntries(mappingEntries);
        }

        @Override
        public String convertToSortProperty(final String sortParameter) throws BadRequestException {
            String sortProperty = conversionMapping.get(sortParameter);
            if (sortProperty == null) {
                throw new BadRequestException("Unknown sort parameter [" + sortParameter + "]");
            }
            return sortProperty;
        }

    }
}

