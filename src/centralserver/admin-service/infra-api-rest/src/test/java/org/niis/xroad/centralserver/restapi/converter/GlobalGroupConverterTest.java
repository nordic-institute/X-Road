/**
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
package org.niis.xroad.centralserver.restapi.converter;

import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.GlobalGroupCodeAndDescriptionDto;
import org.niis.xroad.centralserver.openapi.model.GlobalGroupResourceDto;
import org.niis.xroad.cs.admin.api.domain.GlobalGroup;

import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalGroupConverterTest {
    private final GlobalGroupConverter converter = new GlobalGroupConverter();

    @Test
    void convert() {
        GlobalGroup mockEntity = mockEntity();

        GlobalGroupResourceDto result = converter.convert(mockEntity);

        assertThat(result.getId()).isEqualTo(mockEntity.getId());
        assertThat(result.getCode()).isEqualTo(mockEntity.getGroupCode());
        assertThat(result.getDescription()).isEqualTo(mockEntity.getDescription());
        assertThat(result.getMemberCount()).isEqualTo(mockEntity.getMemberCount());
        assertThat(result.getCreatedAt()).isEqualTo(mockEntity.getCreatedAt().atOffset(ZoneOffset.UTC));
        assertThat(result.getUpdatedAt()).isEqualTo(mockEntity.getUpdatedAt().atOffset(ZoneOffset.UTC));
    }

    @Test
    void toEntity() {
        var globalGroupCodeAndDescription = new GlobalGroupCodeAndDescriptionDto()
                .code("code")
                .description("description");

        var result = converter.toEntity(globalGroupCodeAndDescription);
        assertThat(result.getId()).isZero();
        assertThat(result.getGroupCode()).isEqualTo(globalGroupCodeAndDescription.getCode());
        assertThat(result.getDescription()).isEqualTo(globalGroupCodeAndDescription.getDescription());
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    private GlobalGroup mockEntity() {
        GlobalGroup globalGroup = new GlobalGroup();
        globalGroup.setId(1);
        globalGroup.setGroupCode("code");
        globalGroup.setDescription("description");
        return globalGroup;
    }
}
