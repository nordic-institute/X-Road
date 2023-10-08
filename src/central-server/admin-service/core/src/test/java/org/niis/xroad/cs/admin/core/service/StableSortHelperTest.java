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
package org.niis.xroad.cs.admin.core.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.core.repository.paging.StableSortHelper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

public class StableSortHelperTest {

    StableSortHelper stableSortHelper = new StableSortHelper();

    @Nested
    @DisplayName("addSecondaryIdSort(Pageable original)")
    class AddSecondaryIdSortMethod {
        @Test
        @DisplayName("should append sort identifier for pagination")
        void shouldAppendSortIdentifierForPagination() {
            Sort initialSort = Sort.by("id", "other").descending();
            PageRequest original = PageRequest.of(0, 10).withSort(initialSort);
            Sort expectedSort = Sort.by(
                    Sort.Order.desc("id"),
                    Sort.Order.desc("other"),
                    Sort.Order.asc("id")
            );

            Pageable result = stableSortHelper.addSecondaryIdSort(original);

            assertThat(result.getSort()).isNotEqualTo(initialSort);
            assertThat(result.getSort()).isEqualTo(expectedSort);
        }

        @Test
        @DisplayName("should not append sort identifier for unpaged")
        void shouldNotAppendSort() {
            Pageable original = Pageable.unpaged();

            final Pageable result = stableSortHelper.addSecondaryIdSort(original);

            assertThat(result).isEqualTo(original);
        }
    }
}
