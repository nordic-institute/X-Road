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
package org.niis.xroad.cs.admin.rest.api.converter;

import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.paging.PageRequestDto;
import org.niis.xroad.cs.openapi.model.PagingSortingParametersDto;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_PAGINATION_PROPERTIES;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_SORTING_PROPERTIES;

@Component
public class PageRequestConverter {

    public PageRequestDto convert(PagingSortingParametersDto pagingSorting,
                                  SortParameterConverter sortParameterConverter) {
        try {
            var builder = PageRequestDto.builder()
                    .desc(pagingSorting.getDesc())
                    .limit(pagingSorting.getLimit())
                    .offset(pagingSorting.getOffset());

            if (StringUtils.isNotBlank(pagingSorting.getSort())) {
                builder.jpaSort(sortParameterConverter.convertToSortProperty(pagingSorting.getSort()));
            }

            return builder.build();
        } catch (NullPointerException e) {
            throw new ValidationFailureException(INVALID_PAGINATION_PROPERTIES, e.getMessage());
        }
    }

    public interface SortParameterConverter {
        /**
         * Convert an API-level sort parameter to service-level property name that JPA Data can sort by
         *
         * @throws ValidationFailureException if parameter value cannot be converted
         */
        String convertToSortProperty(String sortParameter) throws ValidationFailureException;
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
        public String convertToSortProperty(final String sortParameter) throws ValidationFailureException {
            String sortProperty = conversionMapping.get(sortParameter);
            if (sortProperty == null) {
                throw new ValidationFailureException(INVALID_SORTING_PROPERTIES, sortParameter);
            }
            return sortProperty;
        }

    }
}

