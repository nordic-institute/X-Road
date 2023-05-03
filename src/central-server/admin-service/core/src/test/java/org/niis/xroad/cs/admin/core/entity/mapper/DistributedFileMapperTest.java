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

package org.niis.xroad.cs.admin.core.entity.mapper;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.dto.File;
import org.niis.xroad.cs.admin.core.entity.DistributedFileEntity;

import static java.time.Instant.ofEpochMilli;
import static org.assertj.core.api.Assertions.assertThat;


class DistributedFileMapperTest {

    private final DistributedFileMapper distributedFileMapper = new DistributedFileMapperImpl();

    @Test
    void toFile() {
        final DistributedFileEntity distributedFileEntity = new DistributedFileEntity(1, "shared-params.xml",
                "SHARED-PARAMETERS", ofEpochMilli(1674212800322L)); // 2023-01-20 11:06:40 UTC
        distributedFileEntity.setFileData(new byte[]{1, 2, 3});

        final File file = distributedFileMapper.toFile(distributedFileEntity);

        assertThat(file.getFilename()).isEqualTo("shared-params_2023-01-20_11 06 40.xml");
        assertThat(file.getData()).isEqualTo(new byte[]{1, 2, 3});
    }

}
