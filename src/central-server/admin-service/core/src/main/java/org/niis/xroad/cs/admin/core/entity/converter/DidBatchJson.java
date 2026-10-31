/*
 * The MIT License
 *
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

package org.niis.xroad.cs.admin.core.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * The JSON text of the DID batch of a member DID registration request and of its per-entry results, as stored in
 * the {@code request_data} and {@code response_data} columns of the request row.
 */
public final class DidBatchJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion(v -> v.withValueInclusion(NON_NULL).withContentInclusion(NON_NULL))
            .build();

    private DidBatchJson() {
    }

    /**
     * @param memberId the encoded member identifier, such as {@code DEV:COM:222}
     * @param did      the DID as received
     */
    public record Entry(String memberId, String did) {
    }

    /**
     * @param did    the DID of the entry as received
     * @param status the answer for the entry
     * @param reason the cause within the status, absent when the entry is stored
     */
    public record Result(String did, Status status, String reason) {
    }

    @Converter
    public static class EntriesConverter extends ListConverter<Entry> {
        public EntriesConverter() {
            super(new TypeReference<>() { });
        }
    }

    @Converter
    public static class ResultsConverter extends ListConverter<Result> {
        public ResultsConverter() {
            super(new TypeReference<>() { });
        }
    }

    abstract static class ListConverter<T> implements AttributeConverter<List<T>, String> {
        private final TypeReference<List<T>> type;

        ListConverter(TypeReference<List<T>> type) {
            this.type = type;
        }

        @Override
        public String convertToDatabaseColumn(List<T> attribute) {
            return attribute == null ? null : MAPPER.writeValueAsString(attribute);
        }

        @Override
        public List<T> convertToEntityAttribute(String dbData) {
            return dbData == null ? null : List.copyOf(MAPPER.readValue(dbData, type));
        }
    }
}
